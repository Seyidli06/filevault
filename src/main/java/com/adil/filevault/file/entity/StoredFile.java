package com.adil.filevault.file.entity;

import com.adil.filevault.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "stored_files",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stored_files_stored_filename",
                        columnNames = "stored_filename"
                ),
                @UniqueConstraint(
                        name = "uk_stored_files_relative_path",
                        columnNames = "relative_path"
                )
        },
        indexes = {
                @Index(
                        name = "idx_stored_files_owner_created_at",
                        columnList = "owner_id, created_at"
                ),
                @Index(
                        name = "idx_stored_files_owner_category",
                        columnList = "owner_id, category"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFile {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_stored_files_owner"
            )
    )
    private User owner;

    @Column(
            name = "title",
            nullable = false,
            length = 150
    )
    private String title;

    @Column(
            name = "description",
            columnDefinition = "TEXT"
    )
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "category",
            nullable = false,
            length = 50
    )
    private FileCategory category;

    @Column(
            name = "original_filename",
            nullable = false,
            length = 255
    )
    private String originalFilename;

    @Column(
            name = "stored_filename",
            nullable = false,
            unique = true,
            length = 100
    )
    private String storedFilename;

    @Column(
            name = "extension",
            nullable = false,
            length = 20
    )
    private String extension;

    @Column(
            name = "media_type",
            nullable = false,
            length = 150
    )
    private String mediaType;

    @Column(
            name = "size_bytes",
            nullable = false
    )
    private long sizeBytes;

    @Column(
            name = "relative_path",
            nullable = false,
            unique = true,
            length = 500
    )
    private String relativePath;

    @Column(
            name = "sha256",
            length = 64
    )
    private String sha256;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (id == null) {
            id = UUID.randomUUID();
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}