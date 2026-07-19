package com.adil.filevault.file.controller;

import com.adil.filevault.auth.security.AuthenticatedUser;
import com.adil.filevault.common.dto.PageResponse;
import com.adil.filevault.file.dto.FileDownload;
import com.adil.filevault.file.dto.FileResponse;
import com.adil.filevault.file.dto.FileUploadForm;
import com.adil.filevault.file.entity.FileCategory;
import com.adil.filevault.file.service.StoredFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.adil.filevault.file.dto.FileMetadataUpdateRequest;

import com.adil.filevault.audit.dto.DownloadRequestContext;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class StoredFileController {

    private final StoredFileService storedFileService;

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(
            @Valid @ModelAttribute FileUploadForm form,

            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        return storedFileService.upload(
                form,
                authenticatedUser
        );
    }

    @GetMapping
    public PageResponse<FileResponse> listOwnFiles(
            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(required = false)
            FileCategory category,

            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        return storedFileService.listOwnFiles(
                authenticatedUser,
                category,
                page,
                size
        );
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID fileId,

            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser,

            HttpServletRequest request
    ) {
        DownloadRequestContext requestContext =
                new DownloadRequestContext(
                        request.getRemoteAddr(),
                        request.getHeader(
                                HttpHeaders.USER_AGENT
                        )
                );

        FileDownload download =
                storedFileService.download(
                        fileId,
                        authenticatedUser,
                        requestContext
                );

        ContentDisposition contentDisposition =
                ContentDisposition
                        .attachment()
                        .filename(
                                download.originalFilename(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.parseMediaType(
                                download.mediaType()
                        )
                )
                .contentLength(download.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .cacheControl(CacheControl.noStore())
                .body(download.resource());
    }

    @PatchMapping("/{fileId}")
    public FileResponse updateMetadata(
            @PathVariable UUID fileId,

            @Valid
            @RequestBody
            FileMetadataUpdateRequest request,

            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        return storedFileService.updateMetadata(
                fileId,
                request,
                authenticatedUser
        );
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID fileId,

            @AuthenticationPrincipal
            AuthenticatedUser authenticatedUser
    ) {
        storedFileService.delete(
                fileId,
                authenticatedUser
        );
    }
}