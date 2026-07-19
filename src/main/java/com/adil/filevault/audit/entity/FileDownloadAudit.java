package com.adil.filevault.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_download_audits")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FileDownloadAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "file_id_snapshot",
            nullable = false,
            updatable = false
    )
    private UUID fileIdSnapshot;

    @Column(
            name = "user_id_snapshot",
            nullable = false,
            updatable = false
    )
    private Long userIdSnapshot;

    @Column(
            name = "user_email_snapshot",
            nullable = false,
            updatable = false,
            length = 255
    )
    private String userEmailSnapshot;

    @Column(
            name = "original_filename_snapshot",
            nullable = false,
            updatable = false,
            length = 255
    )
    private String originalFilenameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "event_type",
            nullable = false,
            updatable = false,
            length = 30
    )
    private DownloadAuditEventType eventType;

    @Column(
            name = "request_ip",
            updatable = false,
            length = 45
    )
    private String requestIp;

    @Column(
            name = "user_agent",
            updatable = false,
            length = 500
    )
    private String userAgent;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}