package com.adil.filevault.audit.dto;

public record DownloadRequestContext(
        String requestIp,
        String userAgent
) {
}