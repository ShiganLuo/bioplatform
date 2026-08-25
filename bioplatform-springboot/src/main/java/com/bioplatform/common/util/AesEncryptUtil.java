package com.bioplatform.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加密工具，用于敏感配置（API Key 等）的加密存储。
 * 加密格式: Base64(iv + ciphertext + tag)
 */
public final class AesEncryptUtil {

    private AesEncryptUtil() {}

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    // 从环境变量或默认值获取密钥（生产环境务必设置 BIOPHATFORM_AES_KEY 环境变量）
    private static String getSecretKey() {
        String key = System.getenv("BIOPLATFORM_AES_KEY");
        if (key == null || key.isEmpty()) {
            key = "REDACTED_AES_KEY"; // 32 bytes for AES-256
        }
        return key;
    }

    private static byte[] getKeyBytes() {
        String key = getSecretKey();
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        // 确保 32 字节 (AES-256)
        byte[] result = new byte[32];
        System.arraycopy(keyBytes, 0, result, 0, Math.min(keyBytes.length, 32));
        return result;
    }

    /**
     * 加密
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 iv + ciphertext，Base64 编码
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return "ENC:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 解密
     */
    public static String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) return ciphertext;
        // 非加密值直接返回
        if (!ciphertext.startsWith("ENC:")) return ciphertext;

        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(4));

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            SecretKeySpec keySpec = new SecretKeySpec(getKeyBytes(), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * 判断是否为加密值
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith("ENC:");
    }

    /**
     * 遮蔽显示（给前端用）
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) return "";
        String real = isEncrypted(value) ? decrypt(value) : value;
        if (real.length() <= 8) return "********";
        return real.substring(0, 3) + "***" + real.substring(real.length() - 4);
    }
}
