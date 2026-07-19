package com.adil.filevault.file.dto;

import org.springframework.core.io.Resource;

public record FileDownload(
        Resource resource,
        String originalFilename,
        String mediaType,
        long sizeBytes
) {
}