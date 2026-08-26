# 端到端安全设计：JWT 双 Token + AES-GCM 加密

> BioPlatform 技术文档：认证授权与敏感配置加密方案。

## 一、JWT 双 Token 机制

### 为什么需要双 Token？

单 Token 的痛点：过期时间短则频繁登出，长则安全风险大，且无法主动吊销。

双 Token 方案：
- **Access Token**：短期有效（1小时），用于 API 认证
- **Refresh Token**：长期有效（14天），仅用于刷新 Access Token

### 后端 Token 生成

```java
public String generateAccessToken(Long userId, String username) {
    return Jwts.builder()
        .subject(username)
        .claim("userId", userId)
        .claim("tokenType", "ACCESS")
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
        .signWith(secretKey)
        .compact();
}
```

### 前端主动刷新

在请求拦截器中检查 token 过期时间，即将过期时主动刷新：

```typescript
axiosInstance.interceptors.request.use(async (config) => {
  const token = getStoredToken()
  if (token && isTokenExpiringSoon(token)) {
    const refreshRes = await axios.post('/api/admin/auth/refreshToken', { refreshToken: token })
    userStore.token = refreshRes.data.accessToken
  }
  return config
})
```

### JWT 过滤器

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String jwt = extractTokenFromRequest(request);
        // 白名单 + 无 Token → 放行
        if (isWhitelisted(request.getRequestURI()) && !StringUtils.hasText(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 有 Token → 验证并设置上下文
        if (StringUtils.hasText(jwt) && jwtTokenProviderUtil.validateToken(jwt)) {
            Long userId = jwtTokenProviderUtil.getUserIdFromToken(jwt);
            LoginUserHolder.setCurrentUser(userId, username);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        try { filterChain.doFilter(request, response); }
        finally { LoginUserHolder.clear(); }
    }
}
```

## 二、AES-GCM 敏感配置加密

### 问题

API Key 等敏感配置需要：传输不被截获、存储不以明文存在、读取时遮蔽显示。

### 方案：端到端加密

```
前端输入 API Key
    ├─ AES-GCM 加密 → 密文传输
    ▼
后端接收
    ├─ 存储为 ENC: 前缀密文
    ▼
后台读取
    ├─ ENC: 前缀 → 解密 → 遮蔽返回 sk-abc***xyz
    ▼
LLM 调用
    ├─ 读取密文 → 解密 → 使用真实 Key
```

### 后端 AES-GCM 实现

```java
public static String encrypt(String plaintext) {
    byte[] iv = new byte[12];
    new SecureRandom().nextBytes(iv);

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(getKeyBytes(), "AES"),
                new GCMParameterSpec(128, iv));
    byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

    // 格式: ENC:Base64(iv + ciphertext + tag)
    byte[] combined = new byte[iv.length + ciphertext.length];
    System.arraycopy(iv, 0, combined, 0, iv.length);
    System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
    return "ENC:" + Base64.getEncoder().encodeToString(combined);
}

public static boolean isEncrypted(String value) {
    return value != null && value.startsWith("ENC:");
}
```

## 三、安全检查清单

| 检查项 | 措施 |
|--------|------|
| 密码存储 | BCrypt 加盐哈希 |
| Token 传输 | HTTPS + Authorization header |
| Token 过期 | Access 1h + Refresh 14d |
| 敏感配置 | AES-GCM 端到端加密 |
| API Key 展示 | 遮蔽返回 sk-abc***xyz |
| Session | STATELESS（不使用 HttpSession） |
| 内存泄漏 | finally 块清理 ThreadLocal |
