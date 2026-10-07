import SparkMD5 from 'spark-md5'

/**
 * 流式计算文件 MD5（2MB/分片，不整载内存）
 * 用途：分片上传的 uploadId 身份、秒传检查、merge 时服务端完整性比对
 * @param onProgress 已读片数 / 总片数
 */
export function calculateFileHash(
  file: File,
  onProgress?: (current: number, total: number) => void
): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunkSize = 2 * 1024 * 1024
    const chunks = Math.ceil(file.size / chunkSize)
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()

    let current = 0
    reader.onerror = () => reject(new Error('读取文件失败，无法计算校验和'))
    reader.onload = e => {
      spark.append(e.target!.result as ArrayBuffer)
      current++
      onProgress?.(current, chunks)
      if (current < chunks) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }

    function loadNext() {
      const start = current * chunkSize
      const end = Math.min(start + chunkSize, file.size)
      reader.readAsArrayBuffer(file.slice(start, end))
    }

    loadNext()
  })
}
