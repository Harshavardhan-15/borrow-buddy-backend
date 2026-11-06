package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.PublicStats;
import com.borrowbuddy.service.PublicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final PublicService publicService;

    public PublicController(PublicService publicService) {
        this.publicService = publicService;
    }

    @GetMapping("/stats")
    public ApiResponse<PublicStats> stats() {
        return ApiResponse.ok(publicService.stats());
    }
}
