package com.yammer.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Generic object storage on Google Cloud Storage (client logos today, menu-item
 * images later). Objects are keyed {@code <prefix>/<uuid><ext>}.
 */
@Service
@Slf4j
public class StorageService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "image/svg+xml");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Storage storage;

    @Value("${gcs.bucket}")
    private String bucket;

    public StorageService(Storage storage) {
        this.storage = storage;
    }

    /** Create the bucket if missing — convenient for the emulator and fresh dev buckets. */
    @PostConstruct
    void ensureBucket() {
        try {
            Bucket existing = storage.get(bucket);
            if (existing == null) {
                storage.create(BucketInfo.of(bucket));
                log.info("Created storage bucket: {}", bucket);
            }
        } catch (Exception e) {
            // On real GCP the bucket should pre-exist and the SA may lack create/list rights.
            log.warn("Could not verify/create bucket '{}': {}", bucket, e.getMessage());
        }
    }

    /** Holds an object's bytes and content type for serving. */
    public record StoredObject(byte[] data, String contentType) {
    }

    /** Stores an uploaded image and returns its object key. */
    public String uploadImage(String prefix, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Not an image");
        }
        String object = prefix + "/" + UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, object))
                .setContentType(contentType)
                .build();
        try {
            storage.create(info, file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read upload", e);
        }
        return object;
    }

    /** Fetches an object's bytes + content type, or empty if it doesn't exist. */
    public Optional<StoredObject> get(String object) {
        if (object == null || object.isBlank()) {
            return Optional.empty();
        }
        Blob blob = storage.get(BlobId.of(bucket, object));
        if (blob == null || !blob.exists()) {
            return Optional.empty();
        }
        String type = blob.getContentType() != null ? blob.getContentType() : "application/octet-stream";
        return Optional.of(new StoredObject(blob.getContent(), type));
    }

    /** Deletes an object (no-op if null/absent). */
    public void delete(String object) {
        if (object == null || object.isBlank()) {
            return;
        }
        try {
            storage.delete(BlobId.of(bucket, object));
        } catch (Exception e) {
            log.warn("Failed to delete object '{}': {}", object, e.getMessage());
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }
}
