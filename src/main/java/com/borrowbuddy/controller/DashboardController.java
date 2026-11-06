package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.dashboard.DashboardStats;
import com.borrowbuddy.service.CurrentUserService;
import com.borrowbuddy.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserService currentUserService;

    public DashboardController(DashboardService dashboardService,
                               CurrentUserService currentUserService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/stats")
    public ApiResponse<DashboardStats> stats() {
        return ApiResponse.ok(dashboardService.stats(currentUserService.requireCurrent()));
    }
}
