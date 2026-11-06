package com.borrowbuddy.dto.report;

import com.borrowbuddy.dto.item.OwnerSummary;
import com.borrowbuddy.entity.enums.ReportStatus;
import com.borrowbuddy.entity.enums.ReportTargetType;

import java.time.Instant;

public record ReportDto(
        Long id,
        OwnerSummary reporter,
        ReportTargetType targetType,
        Long targetId,
        String reason,
        ReportStatus status,
        Instant createdAt
) {
}
