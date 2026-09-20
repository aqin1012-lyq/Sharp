package com.sharp.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** JWT 签发与校验。密钥与有效期可配置（auth.jwt.*）。 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(@Value("${auth.jwt.secret}") String secret,
                      @Value("${auth.jwt.expire-hours:72}") long expireHours) {
        // HS256 要求密钥 ≥ 32 字节；不足时右侧补齐，避免弱密钥启动失败
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < 32; i++) {
                padded[i] = '0';
            }
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMillis = expireHours * 3600_000L;
    }

    /** 以用户名为主体签发 token。 */
    public String generate(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 校验并返回用户名；无效/过期返回 null。 */
    public String parseUsername(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
