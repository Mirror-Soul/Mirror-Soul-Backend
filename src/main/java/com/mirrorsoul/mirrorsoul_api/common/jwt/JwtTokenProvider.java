package com.mirrorsoul.mirrorsoul_api.common.jwt;

import com.mirrorsoul.mirrorsoul_api.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements TokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String createAccessToken(User user) {
        return createToken(user, "access", jwtProperties.accessTokenExpirationSeconds());
    }

    @Override
    public String createRefreshToken(User user) {
        return createToken(user, "refresh", jwtProperties.refreshTokenExpirationSeconds());
    }

    @Override
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    @Override
    public void validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String tokenType = claims.get("tokenType", String.class);
            if (!"refresh".equals(tokenType)) {
                throw new JwtException("Invalid token type");
            }
        } catch (ExpiredJwtException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Invalid refresh token", e);
        }
    }

    private String createToken(User user, String tokenType, long expirationSeconds) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("tokenType", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
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
