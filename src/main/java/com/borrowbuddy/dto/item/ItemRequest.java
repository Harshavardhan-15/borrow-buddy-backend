package com.borrowbuddy.dto.item;

import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.ItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ItemRequest(
        @NotBlank(message = "Item name is required")
        @Size(max = 150, message = "Item name is too long")
        String itemName,

        @NotBlank(message = "Category is required")
        @Size(max = 60, message = "Category is too long")
        String category,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description is too long")
        String description,

        @Size(max = 500, message = "Image URL is too long")
        String imageUrl,

        @NotNull(message = "Item condition is required")
        ItemCondition itemCondition,

        AvailabilityStatus availabilityStatus
) {
}
