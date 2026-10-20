
package com.example.chatbot.service;

import com.example.chatbot.dto.ChatStreamEvent;
import com.example.chatbot.model.ChatConversation;
import com.example.chatbot.model.ChatMessage;
import com.example.chatbot.repository.ChatConversationRepository;
import com.example.chatbot.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final CostGuard costGuard;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    private final String model;
    private final double temperature;
    private final int maxTokens;

    public ChatService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            CostGuard costGuard,
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            @Value("${lmstudio.base-url:http://localhost:1235}") String baseUrl,
            @Value("${lmstudio.model:qwen2.5-coder-1.5b-instruct}") String model,
            @Value("${lmstudio.temperature:0.2}") double temperature,
            @Value("${lmstudio.max-tokens:512}") int maxTokens) {

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer lm-studio")
                .build();

        this.objectMapper = objectMapper;
        this.costGuard = costGuard;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public Flux<ChatStreamEvent> streamAnswer(
            String question,
            String requestedConversationId,
            String clientKey) {

        String cleanedQuestion =
                question == null ? "" : question.trim();

        // Validate the question and apply request limits.
        costGuard.check(cleanedQuestion, clientKey);

        // Create a new conversation or retrieve an existing one.
        ChatConversation conversation;

        if (requestedConversationId == null
                || requestedConversationId.isBlank()) {

            String title = cleanedQuestion.length() > 80
                    ? cleanedQuestion.substring(0, 80)
                    : cleanedQuestion;

            conversation = new ChatConversation();
            conversation.setTitle(title);

            // The entity's @PrePersist method should generate its UUID.
            conversation = conversationRepository.save(conversation);

        } else {
            conversation = conversationRepository
                    .findById(requestedConversationId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Conversation not found: "
                                            + requestedConversationId));
        }

        /*
         * FIX:
         * 'conversation' is reassigned above, so it cannot be captured
         * directly inside a lambda. This reference is assigned only once.
         */
        final ChatConversation currentConversation = conversation;
        final String conversationId = currentConversation.getId();

        // Save the user's question to MySQL.
        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversation(currentConversation);
        userMessage.setRole("user");
        userMessage.setContent(cleanedQuestion);

        messageRepository.save(userMessage);

        currentConversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(currentConversation);

        // Load the latest 12 messages, newest first.
        List<ChatMessage> history =
                messageRepository
                        .findTop12ByConversation_IdOrderByCreatedAtDesc(
                                conversationId);

        // Reverse so the model receives messages chronologically.
        Collections.reverse(history);

        List<Map<String, String>> modelMessages = new ArrayList<>();

        modelMessages.add(Map.of(
                "role", "system",
                "content",
                "You are a helpful, beginner-friendly AI assistant. "
                        + "Use previous messages to understand follow-up "
                        + "questions. Explain concepts clearly and provide "
                        + "examples when helpful. Do not call tools."
        ));

        for (ChatMessage message : history) {
            modelMessages.add(Map.of(
                    "role", message.getRole().toLowerCase(),
                    "content", message.getContent()
            ));
        }

        // Build the request for the LM Studio OpenAI-compatible API.
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", modelMessages,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "stream", true
        );

        StringBuilder completeAnswer = new StringBuilder();

        Flux<ChatStreamEvent> answerStream = webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(
                        new ParameterizedTypeReference<
                                ServerSentEvent<String>>() {
                        })

                // Run subsequent processing on a worker suitable for
                // blocking operations such as JPA repository calls.
                .publishOn(Schedulers.boundedElastic())

                .map(ServerSentEvent::data)

                .filter(data ->
                        data != null
                                && !data.isBlank()
                                && !"[DONE]".equals(data.trim()))

                .map(this::extractContent)

                .filter(content ->
                        content != null && !content.isEmpty())

                // Accumulate the complete answer while streaming tokens.
                .doOnNext(completeAnswer::append)

                .map(token -> new ChatStreamEvent(
                        "token",
                        conversationId,
                        token
                ))

                // FIXED: capture currentConversation, not conversation.
                .doOnComplete(() ->
                        saveAssistantAnswer(
                                currentConversation,
                                completeAnswer.toString()
                        )
                )

                .onErrorMap(
                        WebClientResponseException.class,
                        ex -> new IllegalStateException(
                                "LM Studio returned HTTP "
                                        + ex.getStatusCode()
                                        + ". Check the model ID and server.",
                                ex
                        )
                )

                .onErrorMap(
                        ex -> !(ex instanceof IllegalStateException),
                        ex -> new IllegalStateException(
                                "Could not connect to LM Studio. "
                                        + "Check the configured URL and "
                                        + "make sure the LM Studio server "
                                        + "is running.",
                                ex
                        )
                );

        // Send the conversation ID first, followed by tokens and done.
        return Flux.concat(
                Flux.just(new ChatStreamEvent(
                        "conversation",
                        conversationId,
                        conversationId
                )),
                answerStream,
                Flux.just(new ChatStreamEvent(
                        "done",
                        conversationId,
                        "[DONE]"
                ))
        );
    }

    // Save the complete assistant response to MySQL.
    public void saveAssistantAnswer(
            ChatConversation conversation,
            String answer) {

        if (answer == null || answer.isBlank()) {
            return;
        }

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setConversation(conversation);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);

        messageRepository.save(assistantMessage);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    // Return all conversations, most recently updated first.
    public List<Map<String, Object>> getConversations() {

        return conversationRepository
                .findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(c -> Map.<String, Object>of(
                        "conversationId", c.getId(),
                        "title", c.getTitle() == null
                                ? "New conversation"
                                : c.getTitle(),
                        "createdAt", c.getCreatedAt(),
                        "updatedAt", c.getUpdatedAt()
                ))
                .toList();
    }

    // Retrieve the messages belonging to one conversation.
    public List<Map<String, Object>> getMessages(
            String conversationId) {

        if (!conversationRepository.existsById(conversationId)) {
            throw new IllegalArgumentException(
                    "Conversation not found: " + conversationId);
        }

        return messageRepository
                .findByConversation_IdOrderByCreatedAtAsc(
                        conversationId)
                .stream()
                .map(m -> Map.<String, Object>of(
                        "messageId", m.getId(),
                        "role", m.getRole(),
                        "content", m.getContent(),
                        "createdAt", m.getCreatedAt()
                ))
                .toList();
    }

    // Delete a conversation and its messages.
    public void deleteConversation(String conversationId) {

        ChatConversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found: "
                                        + conversationId));

        // Delete messages first to avoid foreign-key constraint errors.
        messageRepository.deleteByConversation_Id(conversationId);
        conversationRepository.delete(conversation);
    }

    // Extract a text token from an LM Studio streaming response.
    private String extractContent(String data) {

        try {
            JsonNode root = objectMapper.readTree(data);

            JsonNode content = root.path("choices")
                    .path(0)
                    .path("delta")
                    .path("content");

            return content.isTextual()
                    ? content.asText()
                    : "";

        } catch (Exception ex) {
            return "";
        }
    }
}
