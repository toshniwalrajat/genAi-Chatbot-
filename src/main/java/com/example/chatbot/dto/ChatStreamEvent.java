
package com.example.chatbot.dto;

public record ChatStreamEvent(
        String type,
        String conversationId,
        String content
) {
}
