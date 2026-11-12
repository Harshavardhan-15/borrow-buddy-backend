package com.borrowbuddy.dto.item;

import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.ItemCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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

        @DecimalMin(value = "0.0", message = "Daily price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Daily price must have at most 2 decimal places")
        BigDecimal pricePerDay,

        @DecimalMin(value = "0.0", message = "Security deposit cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Security deposit must have at most 2 decimal places")
        BigDecimal securityDeposit,

        @Size(max = 20, message = "Phone number is too long")
        @Pattern(regexp = "^$|^[+]?[0-9][0-9 ()\\-]{6,19}$", message = "Enter a valid phone number")
        String contactPhone,

        AvailabilityStatus availabilityStatus
) {
}
