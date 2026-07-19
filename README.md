# FileVault

[![FileVault CI](https://github.com/Seyidli06/filevault/actions/workflows/ci.yml/badge.svg)](https://github.com/Seyidli06/filevault/actions/workflows/ci.yml)

FileVault is a secure file upload and metadata management REST API built with Java and Spring Boot.

The application allows users to register, authenticate with JWT, upload supported documents, list their own files with pagination, update file metadata, securely download files, and delete files they own.

The main focus of the project is secure file handling, authentication, authorization, validation, database consistency, auditability, and clean REST API design.

---

## Features

### Authentication and Authorization

- User registration
- User login
- JWT-based stateless authentication
- BCrypt password hashing
- Email normalization
- Current authenticated user endpoint
- Ownership-based file authorization
- Consistent JSON responses for `401 Unauthorized` and `403 Forbidden`

### File Management

- Upload files with title, description, and category
- List only the authenticated user's files
- Pagination support
- Category filtering
- Secure file download
- File metadata update
- File deletion
- UUID-based stored filenames
- Storage outside the public and static web directories

### Secure File Validation

- PDF and DOCX extension allowlist
- Maximum upload size of 10 MB
- Empty-file validation
- Client MIME-type consistency validation
- Apache Tika real content-type detection
- PDF file signature validation
- DOCX ZIP structure validation
- Unsafe archive path detection
- Original filename sanitization
- Path traversal protection
- Symbolic-link escape protection
- Real streamed byte-count validation

### Bonus Features

- SHA-256 checksum calculation during upload
- Download audit logging
- Scheduled orphan-file cleanup
- Scheduled temporary-file cleanup
- Physical-file cleanup after database rollback
- Deferred physical deletion after successful database commit

---

## Technology Stack

- Java 21
- Spring Boot 4.0.7
- Spring Web MVC
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- JJWT
- Apache Tika
- Lombok
- Maven Wrapper
- JUnit 5
- MockMvc
- Testcontainers
- Docker

---

## Project Structure

```text
src/main/java/com/adil/filevault
├── audit
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
├── auth
│   ├── controller
│   ├── dto
│   ├── security
│   └── service
├── common
│   └── dto
├── config
├── exception
├── file
│   ├── cleanup
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   ├── storage
│   └── validation
├── user
│   ├── entity
│   └── repository
└── FileVaultApplication.java
```

---

## Prerequisites

Install the following tools before running the project:

- Java 21
- PostgreSQL
- Git
- Docker Desktop for integration tests

A separate Maven installation is not required because the Maven Wrapper is included in the repository.

---

## Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE filevault;
```

Flyway automatically creates and validates the required database tables when the application starts.

The database contains tables for:

- Users
- Stored-file metadata
- File-download audit records

---

## Environment Variables

The application reads sensitive configuration from environment variables.

### Required Variables

```text
DB_PASSWORD
JWT_SECRET_BASE64
```

### Optional Variables

```text
DB_URL
DB_USERNAME
FILE_STORAGE_ROOT
FILE_TEMP_ROOT
FORWARD_HEADERS_STRATEGY
```

Default values:

```text
DB_URL=jdbc:postgresql://localhost:5432/filevault
DB_USERNAME=postgres
FILE_STORAGE_ROOT=./data/filevault/uploads
FILE_TEMP_ROOT=./data/filevault/temp
FORWARD_HEADERS_STRATEGY=NONE
```

### PowerShell Example

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/filevault'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'YOUR_POSTGRES_PASSWORD'
$env:JWT_SECRET_BASE64 = 'YOUR_BASE64_ENCODED_SECRET'
```

Generate a secure 256-bit Base64 JWT secret in PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)

$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)

$rng.Dispose()

$env:JWT_SECRET_BASE64
```

Do not commit real database passwords, JWT secrets, uploaded files, or `.env` files to GitHub.

---

## Running the Application

From the project root, run:

```powershell
.\mvnw.cmd spring-boot:run
```

The application starts at:

```text
http://localhost:8080
```

---

## API Documentation

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI specification:

```text
http://localhost:8080/v3/api-docs
```

Health endpoint:

```text
http://localhost:8080/actuator/health
```

---

## API Endpoints

### Authentication Endpoints

| Method | Endpoint | Description | Authentication |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user | No |
| `POST` | `/api/auth/login` | Login and receive a JWT | No |
| `GET` | `/api/auth/me` | Return the current authenticated user | Yes |

### File Endpoints

| Method | Endpoint | Description | Authentication |
|---|---|---|---|
| `POST` | `/api/files` | Upload a file | Yes |
| `GET` | `/api/files` | List the current user's files | Yes |
| `GET` | `/api/files/{fileId}/download` | Download an owned file | Yes |
| `PATCH` | `/api/files/{fileId}` | Update file metadata | Yes |
| `DELETE` | `/api/files/{fileId}` | Delete an owned file | Yes |

---

## Authentication

### Register

```http
POST /api/auth/register
Content-Type: application/json
```

Request body:

```json
{
  "fullName": "Example User",
  "email": "user@example.com",
  "password": "StrongPassword123"
}
```

Successful status:

```text
201 Created
```

Example response:

```json
{
  "accessToken": "JWT_TOKEN",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": {
    "id": 1,
    "fullName": "Example User",
    "email": "user@example.com",
    "role": "USER"
  }
}
```

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

Request body:

```json
{
  "email": "user@example.com",
  "password": "StrongPassword123"
}
```

Example response:

```json
{
  "accessToken": "JWT_TOKEN",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": {
    "id": 1,
    "fullName": "Example User",
    "email": "user@example.com",
    "role": "USER"
  }
}
```

Use the returned JWT on protected endpoints:

```http
Authorization: Bearer JWT_TOKEN
```

---

## File Upload

```http
POST /api/files
Content-Type: multipart/form-data
Authorization: Bearer JWT_TOKEN
```

Multipart form-data fields:

| Field | Type | Required | Description |
|---|---|---:|---|
| `title` | Text | Yes | Maximum 150 characters |
| `description` | Text | No | Maximum 2,000 characters |
| `category` | Text | Yes | File category |
| `file` | File | Yes | PDF or DOCX, maximum 10 MB |

Supported categories:

```text
CV
CONTRACT
REPORT
CERTIFICATE
DOCUMENT
OTHER
```

Example successful response:

```json
{
  "id": "5cd22a13-f9df-4808-aa02-c88294433f11",
  "title": "My CV",
  "description": "Backend developer CV",
  "category": "CV",
  "originalFilename": "cv.pdf",
  "mediaType": "application/pdf",
  "sizeBytes": 48231,
  "sha256": "64-character-sha256-value",
  "createdAt": "2026-07-19T10:00:00Z",
  "updatedAt": "2026-07-19T10:00:00Z"
}
```

---

## File Listing

```http
GET /api/files?page=0&size=20
Authorization: Bearer JWT_TOKEN
```

Filter by category:

```http
GET /api/files?page=0&size=20&category=CV
Authorization: Bearer JWT_TOKEN
```

Pagination rules:

```text
page >= 0
1 <= size <= 100
```

Users can only list files that belong to their own account.

---

## File Download

```http
GET /api/files/{fileId}/download
Authorization: Bearer JWT_TOKEN
```

Only the owner of the file can download it.

The download response includes security headers:

```text
Content-Disposition: attachment
X-Content-Type-Options: nosniff
Cache-Control: no-store
```

A successful download authorization creates a `DOWNLOAD_GRANTED` audit record.

---

## Update File Metadata

```http
PATCH /api/files/{fileId}
Content-Type: application/json
Authorization: Bearer JWT_TOKEN
```

Example request:

```json
{
  "title": "Updated title",
  "description": "Updated description",
  "category": "DOCUMENT"
}
```

At least one metadata field must be provided.

Only the file owner can update the metadata.

---

## Delete File

```http
DELETE /api/files/{fileId}
Authorization: Bearer JWT_TOKEN
```

Successful status:

```text
204 No Content
```

The database record is deleted inside a transaction.

The physical file is deleted only after the database transaction successfully commits.

When physical deletion fails, the orphan-file cleanup scheduler can remove the remaining file later.

---

## Error Response Format

Application and security errors use a consistent JSON structure:

```json
{
  "timestamp": "2026-07-19T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "fieldErrors": {
    "title": "must not be blank"
  }
}
```

For errors without field validation failures, `fieldErrors` is empty:

```json
{
  "timestamp": "2026-07-19T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Stored file was not found",
  "fieldErrors": {}
}
```

Common HTTP status codes:

| Status | Meaning |
|---:|---|
| `400` | Invalid request, validation error, or unsupported file |
| `401` | Authentication is required or credentials are incorrect |
| `403` | The authenticated user does not have permission |
| `404` | File or endpoint was not found |
| `409` | The request conflicts with existing data |
| `413` | Uploaded file exceeds the maximum size |
| `415` | Request content type is unsupported |
| `500` | Unexpected server or file-storage failure |

---

## Secure File Validation

The upload process uses multiple validation layers.

### Request-Level Validation

Before writing the file to disk, the application validates:

- File is present
- File is not empty
- Declared file size does not exceed 10 MB
- Original filename is valid
- File extension is supported
- Client-provided MIME type is consistent with the extension

### Content-Level Validation

After the upload is written to temporary storage, the application validates:

- Real media type through Apache Tika
- PDF `%PDF-` signature
- DOCX required ZIP entries
- DOCX archive entry count
- Unsafe DOCX archive paths
- Actual streamed byte count

The client-provided content type is never treated as fully trusted.

---

## Storage Security

Uploaded files are not stored using their original filenames.

A random UUID filename is generated:

```text
880711fc-9b84-4f02-9c38-2caa3ec40c10.docx
```

Files are organized by UTC year and month:

```text
data/filevault/uploads/2026/07/880711fc-9b84-4f02-9c38-2caa3ec40c10.docx
```

The application protects storage operations against:

- Path traversal
- Absolute-path injection
- Unsafe relative paths
- Symbolic-link escape
- Direct public URL access
- Original-filename collisions

---

## SHA-256 Checksum

The SHA-256 checksum is calculated while the upload stream is written to temporary storage.

This provides several benefits:

- The file is not loaded fully into memory
- The checksum represents the exact bytes received by the backend
- Duplicate or corrupted files can be detected later
- File integrity can be verified

The checksum is stored in:

```text
stored_files.sha256
```

It is also returned in the file API response.

---

## Download Audit Logging

Each successful file download authorization creates an audit record containing:

```text
file_id_snapshot
user_id_snapshot
user_email_snapshot
original_filename_snapshot
event_type
request_ip
user_agent
occurred_at
```

The event type is:

```text
DOWNLOAD_GRANTED
```

Snapshot fields are used so that audit history remains available even if the original file or user is later deleted.

An audit record is not created when another user attempts to download a file they do not own.

---

## Scheduled Cleanup

The application includes a scheduled cleanup job for orphaned and temporary files.

The scheduler:

- Scans permanent file storage
- Finds files older than the configured grace period
- Compares physical file paths with database metadata
- Deletes files that do not exist in the database
- Deletes expired temporary upload files
- Processes database path checks in batches
- Logs cleanup results
- Continues working even if one cleanup execution fails

Default configuration:

```yaml
filevault:
  cleanup:
    enabled: true
    initial-delay-ms: 60000
    fixed-delay-ms: 21600000
    orphan-grace-period-ms: 86400000
    temporary-grace-period-ms: 3600000
    batch-size: 500
```

Configuration meaning:

```text
initial-delay-ms: 60000
Application waits 1 minute before the first cleanup.

fixed-delay-ms: 21600000
Cleanup runs every 6 hours.

orphan-grace-period-ms: 86400000
A permanent file must be at least 24 hours old before orphan evaluation.

temporary-grace-period-ms: 3600000
A temporary file must be at least 1 hour old before deletion.

batch-size: 500
Database path checks are processed in groups of 500.
```

---

## Database and File Consistency

Database transactions and physical file operations cannot participate in the same atomic transaction.

FileVault handles this problem through compensation logic.

### Upload Flow

```text
Validate request
        ↓
Write file to temporary storage
        ↓
Validate real content
        ↓
Move file to permanent storage
        ↓
Save metadata in PostgreSQL
```

When database persistence fails after the file has moved, rollback cleanup removes the physical file.

### Delete Flow

```text
Verify file ownership
        ↓
Delete metadata in PostgreSQL
        ↓
Commit database transaction
        ↓
Delete physical file
```

Physical deletion occurs only after the database transaction commits.

If physical deletion fails, the orphan cleanup scheduler can delete the remaining file later.

---

## Running Tests

Docker Desktop must be running because integration tests use Testcontainers.

Verify Docker:

```powershell
docker version
```

Run all tests:

```powershell
.\mvnw.cmd clean test
```

The integration tests start a temporary PostgreSQL container automatically.

The tests do not require the local PostgreSQL password because Testcontainers provides temporary database credentials.

---

## Integration Test Coverage

The current integration tests verify:

- Spring application context starts
- Flyway migrations work on a fresh PostgreSQL database
- Protected file endpoint returns `401` without a token
- Invalid registration returns `400`
- Field validation errors use the common API error format
- Duplicate registration returns `409`
- Valid PDF upload returns `201`
- SHA-256 checksum is calculated correctly
- Unsupported `.exe` upload returns `400`
- Another user cannot download an owned file
- Unauthorized ownership access does not create a download audit
- Successful file download returns the original bytes
- Successful file download creates an audit record
- Download security headers are returned

---

## Build Without Tests

To compile and package the project without running tests:

```powershell
.\mvnw.cmd clean package -DskipTests
```

The generated JAR is placed in:

```text
target/
```

---

## Git Ignore Policy

The repository excludes local and sensitive files such as:

```text
target/
.idea/
.vscode/
data/
logs/
.env
.env.*
application-local.yml
application-local.yaml
application-secret.yml
application-secret.yaml
*.log
*.tmp
*-error.txt
```

---

## Security Decisions

Important security decisions in the project include:

- Passwords are hashed with BCrypt
- JWT secrets are externalized
- Database passwords are externalized
- Sessions are stateless
- CSRF is disabled for the stateless JWT API
- Files are accessible only through protected endpoints
- File ownership is enforced in database queries
- Unsupported file extensions are rejected
- Real content type is inspected
- Original filenames are not used as physical filenames
- Storage paths are normalized
- Symbolic-link escapes are blocked
- Download responses disable content sniffing
- Download responses disable caching
- Internal exception details are not exposed to clients

---

## Example Request Flow

```text
Client
  ↓
POST /api/auth/login
  ↓
JWT access token
  ↓
POST /api/files
  ↓
Authentication filter
  ↓
Ownership and validation checks
  ↓
Temporary file storage
  ↓
Content validation
  ↓
Permanent file storage
  ↓
PostgreSQL metadata
  ↓
File response with SHA-256
```

---

## Reverse Proxy Configuration

By default, forwarded proxy headers are not trusted:

```text
FORWARD_HEADERS_STRATEGY=NONE
```

When the application is deployed behind a trusted reverse proxy or load balancer, enable Spring's forwarded-header handling:

```powershell
$env:FORWARD_HEADERS_STRATEGY = 'FRAMEWORK'
```

With this configuration, client information supplied through standard `Forwarded` and `X-Forwarded-*` headers is reflected in the wrapped HTTP request.

The reverse proxy must remove untrusted forwarded headers received from external clients and generate its own trusted headers. Do not enable forwarded-header processing when clients can connect directly to the application and supply arbitrary proxy headers.


## Continuous Integration

The project uses GitHub Actions for continuous integration.

The CI workflow runs automatically on:

- Pushes to the `main` branch
- Pull requests targeting the `main` branch
- Manual workflow execution

The workflow performs:

- Java 21 environment setup
- Maven dependency caching
- Maven build and integration tests
- PostgreSQL Testcontainers tests
- Docker Compose configuration validation
- Docker image build validation
- Test report artifact upload

The workflow configuration is located at:

```text
.github/workflows/ci.yml
```


## Author

GitHub:

```text
Seyidli06
```

Repository:

```text
https://github.com/Seyidli06/filevault
```

---

## License

This project is currently intended for educational and portfolio purposes.