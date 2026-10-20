
package com.example.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageRequest(
        @NotBlank(message = "Prompt cannot be empty")
        @Size(max = 500, message = "Prompt must be 500 characters or fewer")
        String prompt
) {
}
