package com.adil.filevault.file.service;

import com.adil.filevault.audit.dto.DownloadRequestContext;
import com.adil.filevault.audit.service.DownloadAuditService;
import com.adil.filevault.auth.security.AuthenticatedUser;
import com.adil.filevault.common.dto.PageResponse;
import com.adil.filevault.exception.StoredFileNotFoundException;
import com.adil.filevault.file.dto.FileDownload;
import com.adil.filevault.file.dto.FileMetadataUpdateRequest;
import com.adil.filevault.file.dto.FileResponse;
import com.adil.filevault.file.dto.FileUploadForm;
import com.adil.filevault.file.entity.FileCategory;
import com.adil.filevault.file.entity.StoredFile;
import com.adil.filevault.file.repository.StoredFileRepository;
import com.adil.filevault.file.storage.FileStorageService;
import com.adil.filevault.file.storage.StagedFile;
import com.adil.filevault.file.storage.StoredObject;
import com.adil.filevault.file.validation.FileCandidate;
import com.adil.filevault.file.validation.SecureFileValidator;
import com.adil.filevault.user.entity.User;
import com.adil.filevault.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoredFileService {

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final SecureFileValidator fileValidator;
    private final FileStorageService fileStorageService;
    private final DownloadAuditService downloadAuditService;

    @Transactional
    public FileResponse upload(
            FileUploadForm form,
            AuthenticatedUser authenticatedUser
    ) {
        FileCandidate candidate =
                fileValidator.validateRequest(
                        form.getFile()
                );

        StagedFile stagedFile =
                fileStorageService.stage(
                        form.getFile()
                );

        boolean committedToPermanentStorage = false;

        try {
            String detectedMediaType =
                    fileValidator.validateContent(
                            stagedFile.path(),
                            candidate
                    );

            StoredObject storedObject =
                    fileStorageService.commit(
                            stagedFile,
                            candidate.extension()
                    );

            committedToPermanentStorage = true;

            registerRollbackCleanup(
                    storedObject.relativePath()
            );

            User owner =
                    userRepository.getReferenceById(
                            authenticatedUser.id()
                    );

            StoredFile storedFile =
                    StoredFile.builder()
                            .owner(owner)
                            .title(
                                    form.getTitle().strip()
                            )
                            .description(
                                    normalizeDescription(
                                            form.getDescription()
                                    )
                            )
                            .category(
                                    form.getCategory()
                            )
                            .originalFilename(
                                    candidate.originalFilename()
                            )
                            .storedFilename(
                                    storedObject.storedFilename()
                            )
                            .extension(
                                    candidate.extension()
                            )
                            .mediaType(
                                    detectedMediaType
                            )
                            .sizeBytes(
                                    storedObject.sizeBytes()
                            )
                            .relativePath(
                                    storedObject.relativePath()
                            )
                            .sha256(
                                    storedObject.sha256()
                            )
                            .build();


            StoredFile savedFile =
                    storedFileRepository.saveAndFlush(
                            storedFile
                    );

            return FileResponse.from(savedFile);

        } finally {

            if (!committedToPermanentStorage) {
                fileStorageService.discard(
                        stagedFile
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> listOwnFiles(
            AuthenticatedUser authenticatedUser,
            FileCategory category,
            int page,
            int size
    ) {
        validatePagination(page, size);

        PageRequest pageRequest =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );

        Page<StoredFile> storedFiles;

        if (category == null) {
            storedFiles =
                    storedFileRepository
                            .findAllByOwnerId(
                                    authenticatedUser.id(),
                                    pageRequest
                            );

        } else {
            storedFiles =
                    storedFileRepository
                            .findAllByOwnerIdAndCategory(
                                    authenticatedUser.id(),
                                    category,
                                    pageRequest
                            );
        }

        Page<FileResponse> responsePage =
                storedFiles.map(
                        FileResponse::from
                );

        return PageResponse.from(responsePage);
    }

    @Transactional
    public FileDownload download(
            UUID fileId,
            AuthenticatedUser authenticatedUser,
            DownloadRequestContext requestContext
    ) {
        StoredFile storedFile =
                findOwnedFile(
                        fileId,
                        authenticatedUser.id()
                );

        Resource resource =
                fileStorageService.load(
                        storedFile.getRelativePath()
                );


        downloadAuditService.recordGranted(
                storedFile,
                authenticatedUser,
                requestContext
        );

        return new FileDownload(
                resource,
                storedFile.getOriginalFilename(),
                storedFile.getMediaType(),
                storedFile.getSizeBytes()
        );
    }

    @Transactional
    public FileResponse updateMetadata(
            UUID fileId,
            FileMetadataUpdateRequest request,
            AuthenticatedUser authenticatedUser
    ) {
        validateMetadataUpdateRequest(request);

        StoredFile storedFile =
                findOwnedFile(
                        fileId,
                        authenticatedUser.id()
                );

        if (request.title() != null) {
            storedFile.setTitle(
                    normalizeRequiredTitle(
                            request.title()
                    )
            );
        }

        if (request.description() != null) {
            storedFile.setDescription(
                    normalizeDescription(
                            request.description()
                    )
            );
        }

        if (request.category() != null) {
            storedFile.setCategory(
                    request.category()
            );
        }

        StoredFile updatedFile =
                storedFileRepository.saveAndFlush(
                        storedFile
                );

        return FileResponse.from(updatedFile);
    }

    @Transactional
    public void delete(
            UUID fileId,
            AuthenticatedUser authenticatedUser
    ) {
        StoredFile storedFile =
                findOwnedFile(
                        fileId,
                        authenticatedUser.id()
                );

        String relativePath =
                storedFile.getRelativePath();


        storedFileRepository.delete(storedFile);
        storedFileRepository.flush();

        registerAfterCommitDeletion(
                relativePath
        );
    }

    private StoredFile findOwnedFile(
            UUID fileId,
            Long ownerId
    ) {
        return storedFileRepository
                .findByIdAndOwnerId(
                        fileId,
                        ownerId
                )
                .orElseThrow(() ->
                        new StoredFileNotFoundException(
                                fileId
                        )
                );
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number must not be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }
    }

    private void validateMetadataUpdateRequest(
            FileMetadataUpdateRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Metadata update request must not be null"
            );
        }

        if (
                request.title() == null
                        && request.description() == null
                        && request.category() == null
        ) {
            throw new IllegalArgumentException(
                    "At least one metadata field must be provided"
            );
        }
    }

    private String normalizeRequiredTitle(
            String title
    ) {
        String normalizedTitle =
                title.strip();

        if (normalizedTitle.isEmpty()) {
            throw new IllegalArgumentException(
                    "Title must not be blank"
            );
        }

        return normalizedTitle;
    }

    private String normalizeDescription(
            String description
    ) {
        if (
                description == null
                        || description.isBlank()
        ) {
            return null;
        }

        return description.strip();
    }

    private void registerRollbackCleanup(
            String relativePath
    ) {
        if (
                !TransactionSynchronizationManager
                        .isSynchronizationActive()
        ) {
            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(
                                    int status
                            ) {
                                if (
                                        status
                                                == STATUS_COMMITTED
                                ) {
                                    return;
                                }

                                try {
                                    fileStorageService.delete(
                                            relativePath
                                    );

                                } catch (
                                        RuntimeException exception
                                ) {
                                    log.error(
                                            "Rollback file cleanup failed: {}",
                                            relativePath,
                                            exception
                                    );
                                }
                            }
                        }
                );
    }

    private void registerAfterCommitDeletion(
            String relativePath
    ) {
        if (
                !TransactionSynchronizationManager
                        .isSynchronizationActive()
        ) {
            throw new IllegalStateException(
                    "No active transaction for file deletion"
            );
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {
                                try {
                                    fileStorageService.delete(
                                            relativePath
                                    );

                                } catch (
                                        RuntimeException exception
                                ) {

                                    log.error(
                                            "Physical file deletion failed "
                                                    + "after database commit. "
                                                    + "Path: {}",
                                            relativePath,
                                            exception
                                    );
                                }
                            }
                        }
                );
    }
}