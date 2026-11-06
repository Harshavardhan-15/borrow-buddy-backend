package com.borrowbuddy.controller;

import com.borrowbuddy.dto.ApiResponse;
import com.borrowbuddy.dto.PageResponse;
import com.borrowbuddy.dto.notification.NotificationDto;
import com.borrowbuddy.service.CurrentUserService;
import com.borrowbuddy.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService,
                                  CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.list(
                currentUserService.requireCurrent(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(
                currentUserService.requireCurrent())));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationDto> markRead(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(
                currentUserService.requireCurrent(), id));
    }

    @PutMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.ok(Map.of("updated", notificationService.markAllRead(
                currentUserService.requireCurrent())));
    }
}
