package com.adil.filevault.audit.repository;

import com.adil.filevault.audit.entity.FileDownloadAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileDownloadAuditRepository
        extends JpaRepository<FileDownloadAudit, Long> {
}