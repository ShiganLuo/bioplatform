# 大文件分片上传 + 断点续传 + 秒传

> BioPlatform 技术文档：生信数据文件的大文件上传方案。（2026-10-07 与实现同步）

## 背景

生信平台需要上传 FASTQ、BAM 等大文件（数 GB），传统单次上传的问题：网络中断需重新上传、浏览器内存溢出、无法并行上传、无法检测重复文件。

## 方案设计

```
前端选择文件（>5MB 走分片）
    ├─ spark-md5 计算文件 MD5（2MB 分片读取，不占内存，状态 hashing）
    ├─ MD5 即 uploadId（断点续传身份，跨会话有效）
    ├─ 调 /check-instant 查同项目是否已有同内容文件
    │     └─ 命中 → 秒传：直接返回既有记录，不传任何字节
    ├─ 调 /upload-status 查询已上传分片
    │     ├─ 部分已上传 → 断点续传（跳过已有分片）
    │     └─ 全新文件 → 逐片上传 → /merge-chunks 合并
    └─ merge 时服务端流式复算 MD5 与前端比对，不符即删文件报错
```

## 参数

| 项 | 值 |
|----|----|
| 分片大小 | 10MB（`CHUNK_SIZE`） |
| 并发数 | 3（`MAX_CONCURRENT`） |
| 单片重试 | 3 次，退避 1s×attempt，单片超时 2 分钟 |
| 分片阈值 | >5MB 走分片（`shouldUseChunkUpload`） |
| hash 算法 | MD5（spark-md5，2MB/片流式）；仅用于去重与传输校验，非安全签名 |

## 后端实现

### 分片存储结构

```
{uploadPath}/_chunks/{uploadId}/
    0           ← 第 0 片（文件名即索引）
    1           ← 第 1 片
    meta.json   ← {"fileName":"sample.fastq","totalChunks":512}
```

- `uploadId` 即文件 MD5 hex（路径安全字符；后端校验拒绝 `..`、`/`、`\` 防路径遍历）。
- 合并成功后 `_chunks/{uploadId}/` 目录即删除。

### 接口（`ChunkUploadController`，均挂 `@OperLog`）

```java
// 上传单个分片
POST /api/admin/datafiles/upload-chunk   // chunk, uploadId, chunkIndex, totalChunks, fileName

// 查询已上传分片（断点续传）
GET  /api/admin/datafiles/upload-status  // → { uploadedChunks: [0,1,...] }

// 秒传检查（同项目内同内容）
GET  /api/admin/datafiles/check-instant  // fileHash, projectId → DataFile | null

// 合并（fileHash 用于完整性校验，可空兼容老客户端）
POST /api/admin/datafiles/merge-chunks   // uploadId, fileName, projectId, fileHash
```

### 合并与完整性校验（`ChunkUploadServiceImpl.mergeChunks`）

- 校验分片齐全 → 目标文件 `UUID + 原扩展名` 存入项目目录；
- 逐片 `InputStream` 读 1MB buffer，**边写边喂 `MessageDigest`（MD5）**——与合并同量级开销；
- 合并后比对：`fileHash` 非空且 ≠ 实算值 → 删除目标文件 + 抛 `IllegalStateException`（HTTP 422），**不入库**；
- 入库 `content_hash` 用**服务端实算值**，不信任前端声明；
- 老客户端不带 `fileHash` 时不校验（兼容），但仍实算入库。

### 秒传检查

`selectByHashAndProject`：`WHERE content_hash = ? AND project_id = ? ORDER BY id DESC LIMIT 1`。
命中返回既有 `DataFile` 记录（不新建行、不复制文件）；范围限定**同项目内**（跨项目涉及复制/引用计数，未做）。
历史文件 `content_hash` 为 NULL，不参与秒传，正常上传不受影响。

## 前端实现

### spark-md5 流式计算（`src/utils/fileHash.ts`）

```typescript
export function calculateFileHash(file, onProgress?): Promise<string> {
  // 2MB/片 FileReader + SparkMD5.ArrayBuffer 逐片 append，end() 得 hex
}
```

### 上传主流程（`src/utils/chunkUpload.ts`）

1. `status='hashing'` 进度 → `fileHash = await calculateFileHash(file)` → `uploadId = fileHash`；
2. `GET /check-instant` 命中 → 进度置 100% done，直接返回（秒传）；
3. `GET /upload-status` 取已传分片 → pending = 未传分片（断点续传跳过）；
4. 3 个 worker 并发从 pending 抢片，`file.slice()` → FormData → `/upload-chunk`，失败退避重试；
5. `POST /merge-chunks`（带 `fileHash`）→ 返回 `DataFile`。

进度状态：`hashing → uploading → merging → done`；DataView 在 hashing 阶段显示
`文件名（计算校验和…）`。

## 踩坑总结

| 问题 | 解决 |
|------|------|
| 大文件 hash 计算卡顿 | spark-md5 分片读取，每片 2MB，UI 给 hashing 状态 |
| 网络中断 | 断点续传：MD5 即 uploadId，查 `/upload-status` 跳过已有分片 |
| 重复文件浪费带宽/存储 | 秒传：`/check-instant` 同项目同 hash 直接复用记录 |
| 传输损坏无感知 | merge 流式复算 MD5 比对，不符删文件报 422 |
| uploadId 身份不可靠 | 旧实现用 `name+size+mtime` 的 32 位 hash，碰撞且 mtime 变即失效；现用内容 MD5 |
| 入库信任前端 | content_hash 以服务端实算值为准 |
| 老客户端/旧上传 | 不带 fileHash 不校验（兼容）；**uploadId 算法已变，进行中的旧格式中断上传需重传一遍**（一次性成本） |
| 内存溢出 | 分片传输 + 流式合并，从不整载文件 |
