package com.asms.support.api.dto;

import com.asms.support.domain.TicketCategory;
import com.asms.support.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 200, message = "Title must be 5-200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description max 2000 characters")
        String description,

        @NotNull(message = "Category is required")
        TicketCategory category,

        @NotNull(message = "Priority is required")
        TicketPriority priority,

        @NotBlank(message = "Society ID is required")
        String societyId
) {}
