package com.bioplatform.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT token utility for generating, parsing, and validating tokens.
 */
@Component
public class JwtTokenProviderUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProviderUtil.class);

    private static final String USER_ID_CLAIM = "userId";
    private static final String USERNAME_CLAIM = "username";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProviderUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms:3600000}") long accessTokenExpiryMs,
            @Value("${jwt.refresh-token-expiry-ms:604800000}") long refreshTokenExpiryMs) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException | DecodingException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            log.warn("jwt.secret 不是 Base64，已回退为 UTF-8 原文密钥；建议后续改为 Base64 编码");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    /**
     * Generate an access token for the given user.
     */
    public String generateAccessToken(Long userId, String username) {
        return generateToken(userId, username, ACCESS_TOKEN_TYPE, accessTokenExpiryMs);
    }

    /**
     * Generate a refresh token for the given user.
     */
    public String generateRefreshToken(Long userId, String username) {
        return generateToken(userId, username, REFRESH_TOKEN_TYPE, refreshTokenExpiryMs);
    }

    /**
     * Extract user ID from token.
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get(USER_ID_CLAIM, Long.class);
    }

    /**
     * Extract username from token.
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get(USERNAME_CLAIM, String.class);
    }

    /**
     * Validate a token string.
     *
     * @return true if the token is valid and not expired
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (SecurityException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Check if token will expire within the given milliseconds.
     * Returns true if token is already expired or will expire soon.
     */
    public boolean isTokenExpiringSoon(String token, long withinMs) {
        try {
            Claims claims = parseClaims(token);
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis() < withinMs;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private String generateToken(Long userId, String username, String tokenType, long expiryMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .subject(username)
                .claim(USER_ID_CLAIM, userId)
                .claim(USERNAME_CLAIM, username)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
