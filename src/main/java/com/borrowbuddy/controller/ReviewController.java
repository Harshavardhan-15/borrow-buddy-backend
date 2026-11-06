package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.review.CreateReviewRequest;
import com.borrowbuddy.dto.review.ReviewDto;
import com.borrowbuddy.dto.review.ReviewSummaryDto;
import com.borrowbuddy.dto.transaction.TransactionDto;
import com.borrowbuddy.service.CurrentUserService;
import com.borrowbuddy.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public ReviewController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewDto> create(@Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.ok("Review submitted",
                reviewService.create(currentUserService.requireCurrent(), request));
    }

    @GetMapping("/pending")
    public ApiResponse<List<TransactionDto>> pending() {
        return ApiResponse.ok(reviewService.pending(currentUserService.requireCurrent()));
    }

    @GetMapping("/mine")
    public ApiResponse<List<ReviewDto>> mine() {
        return ApiResponse.ok(reviewService.given(currentUserService.requireCurrent()));
    }

    @GetMapping("/{studentId}")
    public ApiResponse<ReviewSummaryDto> forStudent(@PathVariable Long studentId) {
        return ApiResponse.ok(reviewService.forStudent(studentId));
    }
}
