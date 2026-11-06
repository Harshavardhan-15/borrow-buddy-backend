package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.report.CreateReportRequest;
import com.borrowbuddy.dto.report.ReportDto;
import com.borrowbuddy.entity.enums.ReportStatus;
import com.borrowbuddy.service.CurrentUserService;
import com.borrowbuddy.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserService currentUserService;

    public ReportController(ReportService reportService, CurrentUserService currentUserService) {
        this.reportService = reportService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportDto> create(@Valid @RequestBody CreateReportRequest request) {
        return ApiResponse.ok("Report submitted. Thank you for keeping BorrowBuddy safe.",
                reportService.create(currentUserService.requireCurrent(), request));
    }

    @GetMapping("/api/admin/reports")
    public ApiResponse<List<ReportDto>> list(@RequestParam(required = false) ReportStatus status) {
        return ApiResponse.ok(reportService.list(currentUserService.requireCurrent(), status));
    }

    @PutMapping("/api/admin/reports/{id}")
    public ApiResponse<ReportDto> updateStatus(@PathVariable Long id,
                                               @RequestParam ReportStatus status) {
        return ApiResponse.ok("Report updated",
                reportService.updateStatus(currentUserService.requireCurrent(), id, status));
    }
}
