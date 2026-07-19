package com.adil.filevault.file.dto;

import com.adil.filevault.file.entity.FileCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class FileUploadForm {

    @NotBlank
    @Size(max = 150)
    private String title;

    @Size(max = 2_000)
    private String description;

    @NotNull
    private FileCategory category;

    @NotNull
    private MultipartFile file;
}