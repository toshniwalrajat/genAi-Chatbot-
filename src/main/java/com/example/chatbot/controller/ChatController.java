
package com.example.chatbot.controller;

import com.example.chatbot.dto.ChatRequest;
import com.example.chatbot.dto.ChatStreamEvent;
import com.example.chatbot.service.ChatService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // Stream the AI answer and return the conversation ID.
    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest servletRequest) {

        return chatService.streamAnswer(
                        request.question(),
                        request.conversationId(),
                        servletRequest.getRemoteAddr())
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event.type())
                        .id(event.conversationId())
                        .data(event.content())
                        .build());
    }

    // List previous conversations.
    @GetMapping("/conversations")
    public List<Map<String, Object>> getConversations() {
        return chatService.getConversations();
    }

    // Load all messages from one conversation.
    @GetMapping("/conversations/{id}/messages")
    public List<Map<String, Object>> getMessages(
            @PathVariable String id) {
        return chatService.getMessages(id);
    }

    // Delete a conversation and its messages.
    @DeleteMapping("/conversations/{id}")
    public Map<String, String> deleteConversation(
            @PathVariable String id) {

        chatService.deleteConversation(id);

        return Map.of(
                "message",
                "Conversation deleted successfully");
    }

    @GetMapping("/health")
    public String health() {
        return "Chatbot backend is running. "
                + "Make sure LM Studio server is running too.";
    }
}
