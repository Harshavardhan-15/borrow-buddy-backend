package com.borrowbuddy.dto.dashboard;

import com.borrowbuddy.dto.notification.NotificationDto;
import com.borrowbuddy.dto.transaction.TransactionDto;

import java.util.List;

public record DashboardStats(
        long listedItems,
        long activeBorrowRequests,
        long pendingLendingRequests,
        long currentlyBorrowed,
        long completedBorrows,
        long completedLends,
        long unreadNotifications,
        double averageRating,
        List<TransactionDto> upcomingReturns,
        List<NotificationDto> recentActivity
) {
}
