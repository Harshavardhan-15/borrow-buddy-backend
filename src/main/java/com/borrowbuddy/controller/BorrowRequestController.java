package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.borrow.BorrowRequestDto;
import com.borrowbuddy.dto.borrow.CreateBorrowRequest;
import com.borrowbuddy.service.BorrowRequestService;
import com.borrowbuddy.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/borrow-requests")
public class BorrowRequestController {

    private final BorrowRequestService borrowRequestService;
    private final CurrentUserService currentUserService;

    public BorrowRequestController(BorrowRequestService borrowRequestService,
                                   CurrentUserService currentUserService) {
        this.borrowRequestService = borrowRequestService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BorrowRequestDto> create(@Valid @RequestBody CreateBorrowRequest request) {
        return ApiResponse.ok("Borrow request sent",
                borrowRequestService.create(currentUserService.requireCurrent(), request));
    }

    @GetMapping("/sent")
    public ApiResponse<List<BorrowRequestDto>> sent() {
        return ApiResponse.ok(borrowRequestService.sent(currentUserService.requireCurrent()));
    }

    @GetMapping("/received")
    public ApiResponse<List<BorrowRequestDto>> received() {
        return ApiResponse.ok(borrowRequestService.received(currentUserService.requireCurrent()));
    }

    @GetMapping("/{id}")
    public ApiResponse<BorrowRequestDto> get(@PathVariable Long id) {
        return ApiResponse.ok(borrowRequestService.get(currentUserService.requireCurrent(), id));
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<BorrowRequestDto> approve(@PathVariable Long id) {
        return ApiResponse.ok("Request approved",
                borrowRequestService.approve(currentUserService.requireCurrent(), id));
    }

    @PutMapping("/{id}/reject")
    public ApiResponse<BorrowRequestDto> reject(@PathVariable Long id) {
        return ApiResponse.ok("Request rejected",
                borrowRequestService.reject(currentUserService.requireCurrent(), id));
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<BorrowRequestDto> cancel(@PathVariable Long id) {
        return ApiResponse.ok("Request cancelled",
                borrowRequestService.cancel(currentUserService.requireCurrent(), id));
    }
}
