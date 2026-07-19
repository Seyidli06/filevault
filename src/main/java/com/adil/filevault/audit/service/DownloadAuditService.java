package com.adil.filevault.audit.service;

import com.adil.filevault.audit.dto.DownloadRequestContext;
import com.adil.filevault.audit.entity.DownloadAuditEventType;
import com.adil.filevault.audit.entity.FileDownloadAudit;
import com.adil.filevault.audit.repository.FileDownloadAuditRepository;
import com.adil.filevault.auth.security.AuthenticatedUser;
import com.adil.filevault.file.entity.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DownloadAuditService {

    private final FileDownloadAuditRepository auditRepository;

    @Transactional
    public void recordGranted(
            StoredFile storedFile,
            AuthenticatedUser authenticatedUser,
            DownloadRequestContext requestContext
    ) {
        FileDownloadAudit audit = FileDownloadAudit.builder()
                .fileIdSnapshot(storedFile.getId())
                .userIdSnapshot(authenticatedUser.id())
                .userEmailSnapshot(
                        authenticatedUser.getUsername()
                )
                .originalFilenameSnapshot(
                        storedFile.getOriginalFilename()
                )
                .eventType(
                        DownloadAuditEventType.DOWNLOAD_GRANTED
                )
                .requestIp(
                        normalize(
                                requestContext.requestIp(),
                                45
                        )
                )
                .userAgent(
                        normalize(
                                requestContext.userAgent(),
                                500
                        )
                )
                .build();

        auditRepository.save(audit);
    }

    private String normalize(
            String value,
            int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.strip();

        if (normalized.length() <= maximumLength) {
            return normalized;
        }

        return normalized.substring(0, maximumLength);
    }
}