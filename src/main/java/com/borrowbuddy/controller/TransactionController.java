package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.transaction.HandoverRequest;
import com.borrowbuddy.dto.transaction.QrCodeDto;
import com.borrowbuddy.dto.transaction.TransactionDto;
import com.borrowbuddy.service.CurrentUserService;
import com.borrowbuddy.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    public TransactionController(TransactionService transactionService,
                                 CurrentUserService currentUserService) {
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<List<TransactionDto>> list() {
        return ApiResponse.ok(transactionService.list(currentUserService.requireCurrent()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionDto> get(@PathVariable Long id) {
        return ApiResponse.ok(transactionService.get(currentUserService.requireCurrent(), id));
    }

    @GetMapping("/{id}/qr")
    public ApiResponse<QrCodeDto> qr(@PathVariable Long id) {
        return ApiResponse.ok(transactionService.getQrCode(currentUserService.requireCurrent(), id));
    }

    @PutMapping("/{id}/handover")
    public ApiResponse<TransactionDto> handover(@PathVariable Long id,
                                                @Valid @RequestBody HandoverRequest request) {
        return ApiResponse.ok("Handover confirmed",
                transactionService.confirmHandover(currentUserService.requireCurrent(), id, request.token()));
    }

    @PostMapping("/scan")
    public ApiResponse<TransactionDto> scan(@Valid @RequestBody HandoverRequest request) {
        return ApiResponse.ok("Handover confirmed",
                transactionService.confirmHandoverByToken(currentUserService.requireCurrent(), request.token()));
    }

    @PutMapping("/{id}/return")
    public ApiResponse<TransactionDto> returnItem(@PathVariable Long id) {
        return ApiResponse.ok("Item returned successfully",
                transactionService.confirmReturn(currentUserService.requireCurrent(), id));
    }
}
