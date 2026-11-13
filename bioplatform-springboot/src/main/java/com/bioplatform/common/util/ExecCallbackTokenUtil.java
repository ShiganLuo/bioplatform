package com.bioplatform.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 执行回调令牌:executionId + 共享密钥(jwt.secret)的 HMAC-SHA256。
 * 派发时注入执行环境(BIPLATFORM_CALLBACK_TOKEN),流程框架终止/完成时凭此回调,
 * 无需给流程侧发 JWT。
 *
 * @author luosg
 */
@Component
public class ExecCallbackTokenUtil {

    private final byte[] keyBytes;

    public ExecCallbackTokenUtil(@Value("${jwt.secret}") String secret) {
        byte[] key;
        try {
            key = java.util.Base64.getDecoder().decode(secret);
            if (key.length < 32) {
                key = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            key = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.keyBytes = key;
    }

    public String token(long executionId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] sig = mac.doFinal(("exec-callback:" + executionId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(sig.length * 2);
            for (byte b : sig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成回调令牌失败", e);
        }
    }

    public boolean verify(long executionId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        byte[] expected = token(executionId).getBytes(StandardCharsets.UTF_8);
        byte[] actual = token.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
