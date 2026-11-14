# 09 流程框架(Omics/snakemake)对接方案

状态:已定稿(平台侧机制已上线并实测,本文档定义框架侧的对接约定)
适用:Omics 流程框架(run.py / run_module.py 及其调用的 snakemake)

## 1. 范围与目标

平台执行分析时以命令行启动流程框架,框架侧需要对接两件事:

1. **终态回报(必选)**:执行进程终止或正常执行完毕时,通知平台更新执行状态;
2. **进度上报(可选)**:向平台提供精确执行进度(百分比/步骤数/当前阶段)。

平台侧配套机制(已上线):执行环境变量注入、免登录回调接口(HMAC 令牌)、
`progress.jsonl` 轮询读取、进程存活探测兜底、执行日志实时落盘。

## 2. 平台侧已提供的机制

### 2.1 执行环境变量(每次执行启动时注入,框架直接读取)

| 变量 | 含义 | 示例 |
|------|------|------|
| `BIPLATFORM_EXEC_ID` | 本次执行 ID | `22` |
| `BIPLATFORM_CALLBACK_TOKEN` | 回调令牌(HMAC-SHA256 十六进制) | `e1b5819...` |
| `BIPLATFORM_CALLBACK_URL` | 回调地址 | `http://127.0.0.1:8083/api/public/executions/callback` |

回调地址可在系统配置 `execution.callbackUrl` 修改(仅超管)。

### 2.2 探测机制(框架无需关心,仅说明语义)

平台每 10 秒探测执行进程是否存在(worker 任务状态 + 产物目录活跃度):
进程存活则无限期等待,**无任何墙钟超时**;确定性丢失(任务记录消失且产物
10 分钟无写入)才判失败。因此框架**不需要**为长流程做任何续命/心跳动作,
终态回报是唯一的状态同步义务。

### 2.3 日志(框架无需关心)

命令 stdout/stderr 由平台实时落盘到 `<输出目录>/task_output.<执行id>.log`。
框架自身日志请写文件(如 `--log` 或 Python logging 到文件),**不要依赖
stdout 管道存活**(见 §6 教训)。

## 3. 终态回报(必选)

### 3.1 接口契约

```
POST <BIPLATFORM_CALLBACK_URL>
Content-Type: application/json
```

请求体:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| executionId | int | 是 | 取 `BIPLATFORM_EXEC_ID` |
| token | string | 是 | 取 `BIPLATFORM_CALLBACK_TOKEN` |
| status | string | 是 | `SUCCESS` / `FAILED` / `CANCELLED` 三者之一 |
| message | string | 否 | 失败原因等;FAILED 时平台存入错误日志 |
| exitCode | int | 否 | 仅记录用 |

响应(HTTP 状态恒 200,以 body.code 判定):

| body.code | 含义 | 处理建议 |
|-----------|------|----------|
| 200 | 成功(或执行已终态、幂等忽略) | 无需重试 |
| 400 | 参数缺失/非法 | 修调用,不重试 |
| 403 | 令牌无效 | 检查 token 传递,不重试 |
| 404 | 执行记录不存在 | 不重试 |

语义:

- **幂等**:执行已处于终态时,后续回报直接返回成功、不覆盖既有结论;
- **免登录**:接口在白名单,凭令牌鉴权,不需要 JWT。

### 3.2 令牌算法(与平台一致,便于框架侧自行校验/调试)

```
token = hex( HMAC_SHA256( key, "exec-callback:" + executionId ) )
key   = Base64Decode(jwt.secret)  // 解码结果 ≥ 32 字节时取解码值,否则取 UTF-8 原文
```

正常路径直接使用注入的 `BIPLATFORM_CALLBACK_TOKEN`,无需自行计算。

### 3.3 挂载点建议(run.py)

在进程退出的**所有路径**上报:正常完成、异常、被信号终止(SIGTERM/SIGINT)。

```python
import atexit, json, os, signal, urllib.request

_EXEC_ID = int(os.environ.get("BIPLATFORM_EXEC_ID", "0"))
_TOKEN = os.environ.get("BIPLATFORM_CALLBACK_TOKEN", "")
_URL = os.environ.get("BIPLATFORM_CALLBACK_URL", "")
_final_status = {"status": None}

def report(status: str, message: str = "", exit_code: int = 0):
    if not (_EXEC_ID and _TOKEN and _URL):
        return  # 未经平台执行(本地调试)时无环境变量,静默跳过
    body = json.dumps({
        "executionId": _EXEC_ID, "token": _TOKEN, "status": status,
        "message": message[:20000], "exitCode": exit_code,
    }).encode()
    req = urllib.request.Request(_URL, data=body,
                                 headers={"Content-Type": "application/json"}, method="POST")
    try:
        urllib.request.urlopen(req, timeout=15).read()
    except Exception:
        pass  # 平台有探测兜底,回调失败不阻塞退出

def _on_exit():
    if _final_status["status"] is None:
        report("FAILED", "进程未正常收尾即退出")

def _on_signal(signum, frame):
    report("CANCELLED", f"收到信号 {signum}")
    os._exit(128 + signum)

atexit.register(_on_exit)
signal.signal(signal.SIGTERM, _on_signal)
signal.signal(signal.SIGINT, _on_signal)
```

主流程收尾处:

```python
# 正常完成
_final_status["status"] = "SUCCESS"
report("SUCCESS", exit_code=0)

# 异常分支
_final_status["status"] = "FAILED"
report("FAILED", message=str(err), exit_code=1)
```

## 4. 进度上报(可选,B 层)

不做任何接入时平台自动使用兜底解析(方案 A,已上线):从工作目录
`.snakemake/log/*.log` 解析 `N of M steps (P%) done` 与最新规则行,显示
`百分比 · X/Y · 规则名`。若需要框架自定义进度语义,接入 `progress.jsonl`。

### 4.1 约定:向 `<输出目录>/progress.jsonl` 追加 JSON Lines

平台 10 秒轮询该文件,**以 `progress` 事件为准**,字段:

```json
{"event_type": "progress", "completed_jobs": 23, "total_jobs": 105}
{"event_type": "job_started", "rule_name": "quantify"}
```

| 字段 | 说明 |
|------|------|
| event_type | `progress` / `job_started` / `job_finished` 等 |
| completed_jobs / total_jobs | progress 事件必带,平台换算百分比与 `X/Y` |
| rule_name | job 事件可带,平台显示为当前阶段 |

百分比语义由框架定义(按任务数/按样本数/自定义权重皆可),平台不做二次猜测。

### 4.2 推荐实现:snakemake logger 插件(官方机制)

snakemake ≥ 8 支持 logger 插件(`--logger` 开关;9.22.0 已确认存在)。
插件接收类型化事件:`WORKFLOW_STARTED`(任务总数)、`JOB_STARTED /
JOB_FINISHED / JOB_ERROR`(带规则名)、`PROGRESS`(completed/total),
将其转写为 §4.1 的 JSONL 即可,十几行代码。两条落地路径:

1. **直接用现成插件**:`pip install snakemake-logger-plugin-snakesee`(装进
   `smk` 环境),其事件文件格式与 §4.1 完全兼容(completed_jobs/total_jobs/
   rule_name 同名字段);
2. **自写插件**:实现同格式输出,注册为 logger 插件;run.py 调 snakemake 时
   追加 `--logger <插件名> <输出目录>/progress.jsonl`(具体参数形态以
   `snakemake --help` 的 `--logger` 为准)。

### 4.3 优先级

`progress.jsonl` 一旦存在且含合法 progress 事件,平台即以它为准;
否则回退 §4.1 之外的兜底解析(方案 A)。两者展示格式统一。

## 5. 时序

```
平台                          流程框架(run.py → snakemake)
 │  启动命令(注入 BIPLATFORM_*)                    │
 │────────────────────────────────────────────────>│
 │                                ├─(可选)logger 插件持续追加 progress.jsonl
 │  每 10s 读 progress.jsonl / .snakemake/log       │
 │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│
 │  每 10s 探测进程存活(worker 任务状态+产物活跃度) │
 │<────────────────────────────────────────────────>│
 │                                │ 正常完成 / 异常终止 / 收到信号
 │  POST callback(status, message)│
 │<────────────────────────────────────────────────│
 │  更新执行状态(幂等)                            │
```

## 6. 边界情况与已知教训

1. **stdout 不可靠**:执行进程若与平台之间的输出管道断裂(如执行端容器重建),
   下一次 stdout 写入触发 SIGPIPE 会杀死整个流程。教训已由平台侧缓解
   (输出改落文件),框架侧仍应避免依赖 stdout 存活。
2. **不设超时**:平台无墙钟超时,长流程无需续命;进程退出即结束。
3. **回调失败不阻塞**:平台探测机制兜底(worker 任务终态 / 任务丢失且产物
   停止写入),回调是权威但非唯一通道;失败可重试,重复回报幂等。
4. **输出目录按分析复用**:同目录重跑为增量续跑(snakemake 跳过已完成步骤),
   同分析不支持并发执行(平台拒绝)。框架写 progress.jsonl 时用追加模式("a"),
   重跑覆盖旧事件流即可(平台只读尾部)。

## 7. 联调验收清单

- [ ] 未经平台执行(无 BIPLATFORM_* 环境变量)时,框架行为不变(静默跳过回报);
- [ ] 正常完成 → 平台执行状态 SUCCESS,含 message/exitCode;
- [ ] 人为制造失败 → FAILED,失败原因出现在执行监控的错误信息;
- [ ] `kill -TERM` → CANCELLED;
- [ ] 回调重复发送 → 平台幂等(状态不被覆盖);
- [ ] token 篡改 → 403;
- [ ] (可选)接入 logger 插件后,执行监控进度显示 `X/Y · 阶段名` 与
      progress.jsonl 一致。
