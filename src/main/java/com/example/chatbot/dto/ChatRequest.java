
package com.example.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        // Null or missing means start a new conversation.
        String conversationId,

        @NotBlank(message = "Question must not be empty")
        @Size(
                max = 2000,
                message = "Question must be 2000 characters or fewer"
        )
        String question

) {
}
