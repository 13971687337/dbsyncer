package org.dbsyncer.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public final class JwtTokenUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenUtil.class);

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
        "HC-DBSync-JWT-Secret-Key-2026-Must-Be-At-Least-256-Bits!!".getBytes(StandardCharsets.UTF_8)
    );

    private static final long EXPIRATION_MS = 30 * 60 * 1000L;

    private JwtTokenUtil() {}

    public static String createToken(String username, Map<String, Object> claims) {
        Date now = new Date();
        return Jwts.builder()
            .subject(username)
            .claims(claims)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + EXPIRATION_MS))
            .signWith(SECRET_KEY)
            .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(SECRET_KEY)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public static String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    public static boolean isExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            logger.debug("Token 验证失败: {}", e.getMessage());
            return true;
        }
    }
}
