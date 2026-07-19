package com.adil.filevault.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final String ISSUER = "filevault-api";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final JwtParser jwtParser;

    public JwtService(JwtProperties properties) {
        this.properties = properties;

        byte[] keyBytes = Decoders.BASE64.decode(
                properties.secretBase64()
        );

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);

        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .build();
    }

    public String generateToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(
                properties.accessTokenExpiration()
        );

        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(ISSUER)
                .claim("uid", user.id())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValidFor(
            Claims claims,
            UserDetails userDetails
    ) {
        return claims.getSubject()
                .equalsIgnoreCase(userDetails.getUsername())
                && userDetails.isEnabled();
    }

    public long getExpirationSeconds() {
        return properties
                .accessTokenExpiration()
                .toSeconds();
    }
}