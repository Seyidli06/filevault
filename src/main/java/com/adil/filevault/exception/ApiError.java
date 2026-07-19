package com.adil.filevault.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
) {

    /*
     * Field validation xətası olmayan hallarda
     * köhnə 4 parametrli constructor işləməyə davam edəcək.
     */
    public ApiError(
            Instant timestamp,
            int status,
            String error,
            String message
    ) {
        this(
                timestamp,
                status,
                error,
                message,
                Map.of()
        );
    }
}