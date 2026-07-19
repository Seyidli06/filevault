package com.adil.filevault.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "filevault.cleanup")
public class FileCleanupProperties {

    private boolean enabled = true;

    @Min(0)
    private long initialDelayMs = 60_000;

    @Min(1_000)
    private long fixedDelayMs = 21_600_000;

    @Min(0)
    private long orphanGracePeriodMs = 86_400_000;

    @Min(0)
    private long temporaryGracePeriodMs = 3_600_000;

    @Min(1)
    @Max(5_000)
    private int batchSize = 500;
}