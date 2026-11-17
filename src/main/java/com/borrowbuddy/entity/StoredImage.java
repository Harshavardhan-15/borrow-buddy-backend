package com.borrowbuddy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An image whose bytes live in the database instead of on a disk.
 *
 * <p>Keeping images in MySQL means a deployment needs no persistent volume and
 * uploaded photos survive restarts, redeploys and free-tier hosts with an
 * ephemeral filesystem. The filename is the public URL segment, so existing
 * {@code /uploads/<name>} links keep working unchanged.</p>
 */
@Entity
@Table(name = "stored_images")
@Getter
@Setter
@NoArgsConstructor
public class StoredImage {

    /** Public identifier, e.g. {@code 4b72a97d-....jpg}. */
    @Id
    @Column(name = "filename", length = 80, nullable = false, updatable = false)
    private String filename;

    @Column(name = "content_type", length = 60, nullable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    /** Hex SHA-256 of the bytes, reused as the HTTP ETag. */
    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    /**
     * Raw bytes. The explicit length makes Hibernate pick MySQL's LONGBLOB
     * instead of the default TINYBLOB, which only holds 255 bytes.
     */
    @Lob
    @Column(name = "data", nullable = false, length = Integer.MAX_VALUE)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
