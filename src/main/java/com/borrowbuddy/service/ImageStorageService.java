package com.borrowbuddy.service;

import com.borrowbuddy.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private final Path root;
    private final long maxBytes;

    public ImageStorageService(@Value("${app.upload.dir}") String uploadDir,
                               @Value("${app.upload.max-bytes}") long maxBytes) throws IOException {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        Files.createDirectories(root);
    }

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

        String extension = extensionFor(contentType);
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (!hasValidMagicBytes(header, extension)) {
                throw new BusinessException("The uploaded file is not a valid image");
            }
        } catch (IOException ex) {
            throw new BusinessException("Could not read the uploaded image");
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("Invalid upload path");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("Could not save the uploaded image");
        }
        return "/uploads/" + filename;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
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
}
