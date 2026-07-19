package com.adil.filevault.file.repository;

import com.adil.filevault.file.entity.FileCategory;
import com.adil.filevault.file.entity.StoredFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoredFileRepository
        extends JpaRepository<StoredFile, UUID> {

    Page<StoredFile> findAllByOwnerId(
            Long ownerId,
            Pageable pageable
    );

    Page<StoredFile> findAllByOwnerIdAndCategory(
            Long ownerId,
            FileCategory category,
            Pageable pageable
    );

    Optional<StoredFile> findByIdAndOwnerId(
            UUID fileId,
            Long ownerId
    );

    boolean existsByStoredFilename(String storedFilename);

    boolean existsByRelativePath(String relativePath);

    @Query("""
        select file.relativePath
        from StoredFile file
        where file.relativePath in :relativePaths
        """)
    List<String> findExistingRelativePaths(
            @Param("relativePaths")
            Collection<String> relativePaths
    );
}