package com.borrowbuddy.service;

import com.borrowbuddy.entity.StoredImage;
import com.borrowbuddy.exception.BusinessException;
import com.borrowbuddy.repository.StoredImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploaded images in the database and resolves {@code /uploads/**}
 * requests.
 *
 * <p>Lookup order is database, then the legacy filesystem upload directory,
 * then images bundled on the classpath. The last two keep older deployments and
 * the seeded demo photos working without a migration.</p>
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    /** Rejects path traversal and anything that is not a plain file name. */
    private static final Pattern SAFE_FILENAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");

    private final Path root;
    private final long maxBytes;
    private final StoredImageRepository repository;

    public ImageStorageService(@Value("${app.upload.dir}") String uploadDir,
                               @Value("${app.upload.max-bytes}") long maxBytes,
                               StoredImageRepository repository) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.repository = repository;
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            // Read-only container filesystem: database storage still works.
            log.warn("Upload directory {} is not writable; using database storage only", root);
        }
    }

    /** Validates the upload and stores its bytes in MySQL, returning the public URL. */
    @Transactional
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Please choose an image to upload");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("Image is too large. Maximum size is "
                    + (maxBytes / (1024 * 1024)) + " MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("Only JPG, PNG, WEBP and GIF images are allowed");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("Could not read the uploaded image");
        }
        if (bytes.length > maxBytes) {
            throw new BusinessException("Image is too large. Maximum size is "
                    + (maxBytes / (1024 * 1024)) + " MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }

        String extension = extensionFor(contentType);
        if (!hasValidMagicBytes(bytes, extension)) {
            throw new BusinessException("The uploaded file is not a valid image");
        }

        String filename = UUID.randomUUID() + "." + extension;
        StoredImage image = new StoredImage();
        image.setFilename(filename);
        image.setContentType(contentType);
        image.setByteSize(bytes.length);
        image.setSha256(sha256(bytes));
        image.setData(bytes);
        repository.save(image);
        return "/uploads/" + filename;
    }

    /** Resolves an image by its public filename, or empty when it does not exist. */
    @Transactional(readOnly = true)
    public Optional<ImageData> load(String filename) {
        if (filename == null || !SAFE_FILENAME.matcher(filename).matches() || filename.contains("..")) {
            return Optional.empty();
        }
        Optional<StoredImage> stored = repository.findById(filename);
        if (stored.isPresent()) {
            StoredImage image = stored.get();
            return Optional.of(new ImageData(image.getData(), image.getContentType(), image.getSha256()));
        }
        return readFromDisk(filename).or(() -> readFromClasspath(filename));
    }

    /**
     * Copies the demo photos bundled in the jar into the database when missing.
     * Idempotent, so it is safe to run on every start.
     */
    @Transactional
    public int importBundledDemoImages() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources("classpath*:static/uploads/*");
        } catch (IOException ex) {
            log.warn("Could not scan bundled demo images: {}", ex.getMessage());
            return 0;
        }

        int imported = 0;
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !SAFE_FILENAME.matcher(filename).matches()
                    || repository.existsById(filename)) {
                continue;
            }
            byte[] bytes;
            try (InputStream input = resource.getInputStream()) {
                bytes = input.readAllBytes();
            } catch (IOException ex) {
                log.warn("Could not read bundled image {}: {}", filename, ex.getMessage());
                continue;
            }
            if (bytes.length == 0) {
                continue;
            }
            StoredImage image = new StoredImage();
            image.setFilename(filename);
            image.setContentType(contentTypeFor(filename));
            image.setByteSize(bytes.length);
            image.setSha256(sha256(bytes));
            image.setData(bytes);
            repository.save(image);
            imported++;
        }
        return imported;
    }

    private Optional<ImageData> readFromDisk(String filename) {
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            return Optional.of(new ImageData(bytes, contentTypeFor(filename), sha256(bytes)));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private Optional<ImageData> readFromClasspath(String filename) {
        Resource resource = new ClassPathResource("static/uploads/" + filename);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            return Optional.of(new ImageData(bytes, contentTypeFor(filename), sha256(bytes)));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private boolean hasValidMagicBytes(byte[] header, String extension) {
        if (header.length < 12) {
            return false;
        }
        return switch (extension) {
            case "jpg" -> (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png" -> (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
            case "gif" -> header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8';
            case "webp" -> header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    /** An image ready to be written to an HTTP response. */
    public record ImageData(byte[] bytes, String contentType, String etag) {
    }
}
