package com.adil.filevault.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "filevault.storage")
public record FileStorageProperties(

        @NotNull
        Path rootLocation,

        @NotNull
        Path temporaryLocation,

        @Min(1)
        long maxFileSizeBytes,

        @NotEmpty
        Map<String, String> allowedTypes
) {
}