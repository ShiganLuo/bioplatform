/**
 * AES-GCM 加密工具（前端版）
 * 与后端 AesEncryptUtil 兼容，使用相同的密钥和格式
 * 格式: ENC:Base64(iv + ciphertext + tag)
 */

// 前端加密密钥（与后端 BIOPLATFORM_AES_KEY 一致）
// 生产环境应从后端动态获取或使用环境变量
const SECRET_KEY = 'REDACTED_AES_KEY'

async function getKeyBytes(): Promise<Uint8Array> {
  const encoder = new TextEncoder()
  const keyData = encoder.encode(SECRET_KEY)
  // 确保 32 字节 (AES-256)
  const result = new Uint8Array(32)
  result.set(keyData.slice(0, 32))
  return result
}

/**
 * AES-GCM 加密，返回 ENC:Base64 格式字符串
 */
export async function encrypt(plaintext: string): Promise<string> {
  const keyBytes = await getKeyBytes()
  const iv = crypto.getRandomValues(new Uint8Array(12))

  const key = await crypto.subtle.importKey(
    'raw',
    keyBytes,
    { name: 'AES-GCM' },
    false,
    ['encrypt']
  )

  const encoder = new TextEncoder()
  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, tagLength: 128 },
    key,
    encoder.encode(plaintext)
  )

  // 拼接 iv + ciphertext
  const combined = new Uint8Array(iv.length + encrypted.byteLength)
  combined.set(iv)
  combined.set(new Uint8Array(encrypted), iv.length)

  // Base64 编码
  const base64 = btoa(String.fromCharCode(...combined))
  return 'ENC:' + base64
}

/**
 * 判断是否为已加密值
 */
export function isEncrypted(value: string): boolean {
  return value != null && value.startsWith('ENC:')
}
