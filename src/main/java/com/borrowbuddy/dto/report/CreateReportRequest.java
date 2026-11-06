package com.borrowbuddy.dto.report;

import com.borrowbuddy.entity.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull(message = "Target type is required")
        ReportTargetType targetType,

        @NotNull(message = "Target is required")
        Long targetId,

        @NotBlank(message = "Please describe the problem")
        @Size(max = 1000, message = "Reason is too long")
        String reason
) {
}
