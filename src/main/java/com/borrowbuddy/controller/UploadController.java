package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.service.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final ImageStorageService imageStorageService;

    public UploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = imageStorageService.store(file);
        return ApiResponse.ok("Image uploaded", Map.of("url", url));
    }
}
