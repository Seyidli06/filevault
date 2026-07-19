package com.adil.filevault.file.dto;

import com.adil.filevault.file.entity.FileCategory;
import jakarta.validation.constraints.Size;

public record FileMetadataUpdateRequest(

        @Size(max = 150)
        String title,

        @Size(max = 2_000)
        String description,

        FileCategory category
) {
}