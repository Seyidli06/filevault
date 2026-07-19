package com.adil.filevault.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail(
            EmailAlreadyExistsException exception
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException exception
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Email or password is incorrect"
        );
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiError> handleInvalidFile(
            InvalidFileException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(
            MaxUploadSizeExceededException exception
    ) {
        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File exceeds the maximum allowed size"
        );
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiError> handleFileStorage(
            FileStorageException exception
    ) {
        log.error(
                "File storage operation failed",
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The file storage operation could not be completed"
        );
    }

    @ExceptionHandler(StoredFileNotFoundException.class)
    public ResponseEntity<ApiError> handleStoredFileNotFound(
            StoredFileNotFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    /*
     * @Valid @RequestBody validation xətaları.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleRequestBodyValidation(
            MethodArgumentNotValidException exception
    ) {
        return buildValidationResponse(
                exception.getBindingResult()
        );
    }

    /*
     * @Valid @ModelAttribute validation və binding xətaları.
     * Multipart upload form üçün xüsusilə vacibdir.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBindingValidation(
            BindException exception
    ) {
        return buildValidationResponse(
                exception.getBindingResult()
        );
    }

    /*
     * Məsələn:
     * ?category=INVALID
     * və ya düzgün olmayan UUID.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        String message =
                "Invalid value for parameter: "
                        + exception.getName();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    /*
     * Səhv JSON, yarımçıq body və ya enum üçün
     * mövcud olmayan dəyər.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(
            HttpMessageNotReadableException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request body is malformed or contains an unsupported value"
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingRequestPart(
            MissingServletRequestPartException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Required multipart part is missing: "
                        + exception.getRequestPartName()
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Required request parameter is missing: "
                        + exception.getParameterName()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception
    ) {
        return buildResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "The request Content-Type is not supported"
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException exception
    ) {
        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint"
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(
            NoResourceFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "The requested endpoint was not found"
        );
    }

    /*
     * Race condition zamanı iki eyni email və ya
     * başqa unique constraint pozuntusu ola bilər.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        log.warn(
                "Database constraint violation",
                exception
        );

        return buildResponse(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    /*
     * Gözlənilməyən xətanın daxili məlumatını
     * client-ə qaytarmırıq.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception
    ) {
        log.error(
                "Unexpected application error",
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
    }

    private ResponseEntity<ApiError> buildValidationResponse(
            BindingResult bindingResult
    ) {
        Map<String, String> fieldErrors =
                extractFieldErrors(bindingResult);

        ApiError response = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request validation failed",
                fieldErrors
        );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    private Map<String, String> extractFieldErrors(
            BindingResult bindingResult
    ) {
        Map<String, String> fieldErrors =
                new LinkedHashMap<>();

        for (FieldError fieldError
                : bindingResult.getFieldErrors()) {

            String message =
                    fieldError.getDefaultMessage();

            if (message == null || message.isBlank()) {
                message = "Invalid value";
            }

            fieldErrors.putIfAbsent(
                    fieldError.getField(),
                    message
            );
        }

        return fieldErrors;
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String message
    ) {
        ApiError response = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}