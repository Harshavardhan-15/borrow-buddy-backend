package com.borrowbuddy.controller;

import com.borrowbuddy.service.ImageStorageService;
import com.borrowbuddy.service.ImageStorageService.ImageData;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

/**
 * Serves stored images at their stable {@code /uploads/<filename>} URL.
 *
 * <p>Filenames are random UUIDs, so the bytes never change for a given URL.
 * That makes a one-year immutable cache safe and keeps repeat views off the
 * database.</p>
 */
@RestController
public class ImageController {

    private static final CacheControl IMMUTABLE =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final ImageStorageService imageStorageService;

    public ImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<byte[]> image(
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        Optional<ImageData> found = imageStorageService.load(filename);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        }

        ImageData image = found.get();
        String etag = "\"" + image.etag() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(IMMUTABLE)
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.bytes().length)
                .eTag(etag)
                .cacheControl(IMMUTABLE)
                .body(image.bytes());
    }
}
