package com.adil.filevault.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(
        prefix = "filevault.security.password"
)
public record PasswordSecurityProperties(

        @Min(4)
        @Max(31)
        int bcryptStrength

) {
}