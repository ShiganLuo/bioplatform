# 大文件分片上传 + 断点续传

> BioPlatform 技术文档：生信数据文件的大文件上传方案。

## 背景

生信平台需要上传 FASTQ、BAM 等大文件（数 GB），传统单次上传的问题：网络中断需重新上传、浏览器内存溢出、无法并行上传、无法检测重复文件。

## 方案设计

```
前端选择文件
    ├─ spark-md5 计算文件 hash（分片读取，不占内存）
    ├─ 调用 /upload-status 查询已上传分片
    ├─ 全部已上传 → 秒传
    ├─ 部分已上传 → 断点续传（跳过已有分片）
    └─ 全新文件 → 逐片上传 → /merge-chunks 合并
```

## 后端实现

### 分片存储结构

```
{uploadPath}/_chunks/{uploadId}/
    0           ← 第 0 片
    1           ← 第 1 片
    meta.json   ← {"fileName":"sample.fastq","totalChunks":512}
```

### 上传接口

```java
@PostMapping("/upload-chunk")
public ApiResponse<Map<String, Object>> uploadChunk(
        @RequestParam("chunk") MultipartFile chunk,
        @RequestParam String uploadId,
        @RequestParam int chunkIndex,
        @RequestParam int totalChunks,
        @RequestParam String fileName) {
    chunkUploadService.uploadChunk(uploadId, chunkIndex, totalChunks, fileName, chunk.getBytes());
    return ApiResponse.success(Map.of("uploadId", uploadId, "chunkIndex", chunkIndex));
}

@GetMapping("/upload-status")
public ApiResponse<Map<String, Object>> uploadStatus(@RequestParam String uploadId) {
    List<Integer> uploaded = chunkUploadService.getUploadedChunks(uploadId);
    return ApiResponse.success(Map.of("uploadedChunks", uploaded, "uploadedCount", uploaded.size()));
}

@PostMapping("/merge-chunks")
public ApiResponse<DataFile> mergeChunks(@RequestBody Map<String, Object> params) {
    DataFile file = chunkUploadService.mergeChunks(uploadId, fileName, userId);
    return ApiResponse.success(file);
}
```

## 前端实现

### spark-md5 计算文件 hash

```typescript
async function calculateFileHash(file: File): Promise<string> {
  return new Promise((resolve) => {
    const chunkSize = 2 * 1024 * 1024
    const chunks = Math.ceil(file.size / chunkSize)
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()
    let currentChunk = 0

    reader.onload = (e) => {
      spark.append(e.target!.result as ArrayBuffer)
      currentChunk++
      if (currentChunk < chunks) loadNext()
      else resolve(spark.end())
    }

    function loadNext() {
      const start = currentChunk * chunkSize
      const end = Math.min(start + chunkSize, file.size)
      reader.readAsArrayBuffer(file.slice(start, end))
    }
    loadNext()
  })
}
```

### 断点续传逻辑

```typescript
async function uploadFile(file: File) {
  const fileHash = await calculateFileHash(file)
  const uploadId = fileHash  // 用文件 hash 作为 uploadId

  // 查询已上传的分片
  const status = await getUploadStatus(uploadId)
  const uploadedSet = new Set(status.uploadedChunks)

  // 全部已上传 → 秒传
  if (uploadedSet.size >= chunks) return await mergeChunks(uploadId, file.name)

  // 逐片上传（跳过已上传的）
  for (let i = 0; i < chunks; i++) {
    if (uploadedSet.has(i)) continue  // 断点续传：跳过
    const chunk = file.slice(i * chunkSize, Math.min((i + 1) * chunkSize, file.size))
    await uploadChunk(chunk, uploadId, i, chunks, file.name)
  }

  return await mergeChunks(uploadId, file.name)
}
```

## 踩坑总结

| 问题 | 解决 |
|------|------|
| 大文件 hash 计算卡顿 | spark-md5 分片读取，每片 2MB |
| 网络中断 | 断点续传：查询已上传分片，跳过 |
| 重复文件 | 秒传：文件 hash 相同直接返回 |
| 内存溢出 | 流式合并，不一次性加载 |
