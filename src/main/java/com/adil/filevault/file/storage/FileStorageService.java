package com.adil.filevault.file.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

public interface FileStorageService {

    StagedFile stage(MultipartFile file);

    StoredObject commit(
            StagedFile stagedFile,
            String extension
    );

    Resource load(String relativePath);

    List<StorageFileEntry> findPermanentFilesOlderThan(
            Instant cutoff
    );

    int deleteTemporaryFilesOlderThan(
            Instant cutoff
    );

    void discard(StagedFile stagedFile);

    void delete(String relativePath);
}