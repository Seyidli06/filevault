package com.adil.filevault.auth.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "filevault.security.jwt")
public record JwtProperties(

        @NotBlank
        String secretBase64,

        @NotNull
        Duration accessTokenExpiration
) {
}