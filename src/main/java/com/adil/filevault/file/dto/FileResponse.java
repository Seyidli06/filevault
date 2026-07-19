package com.adil.filevault.file.dto;

import com.adil.filevault.file.entity.FileCategory;
import com.adil.filevault.file.entity.StoredFile;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        String title,
        String description,
        FileCategory category,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String sha256,
        Instant createdAt,
        Instant updatedAt
) {

    public static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.getId(),
                file.getTitle(),
                file.getDescription(),
                file.getCategory(),
                file.getOriginalFilename(),
                file.getMediaType(),
                file.getSizeBytes(),
                file.getSha256(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}