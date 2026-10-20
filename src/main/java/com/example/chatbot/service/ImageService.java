
package com.example.chatbot.service;

import com.example.chatbot.dto.ImageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.Map;

@Service
public class ImageService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final boolean mockMode;
    private final String apiKey;
    private final String model;

    public ImageService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.image-model:gpt-image-1-mini}") String model,
            @Value("${image.mock-mode:true}") boolean mockMode) {

        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .build();

        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.mockMode = mockMode;
    }

    public ImageResponse generateImage(String prompt) {

        String cleanedPrompt = prompt == null ? "" : prompt.trim();

        if (cleanedPrompt.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt cannot be empty");
        }

        if (cleanedPrompt.length() > 500) {
            throw new IllegalArgumentException(
                    "Prompt must be 500 characters or fewer");
        }

        // Mock mode does not call OpenAI or use an API key.
        if (mockMode) {
            return new ImageResponse(
                    "https://placehold.co/1024x1024/png?text=Mock+Generated+Image",
                    "Mock image response returned successfully"
            );
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is missing. Configure OPENAI_API_KEY.");
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", cleanedPrompt,
                    "n", 1,
                    "size", "1024x1024",
                    "quality", "low",
                    "output_format", "png"
            );

            String responseBody = webClient.post()
                    .uri("/images/generations")
                    .headers(headers ->
                            headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException(
                        "OpenAI returned an empty response");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode imageData = root.path("data").path(0);
            String base64Image = imageData.path("b64_json").asText("");

            if (base64Image.isBlank()) {
                throw new IllegalStateException(
                        "OpenAI did not return image data");
            }

            String imageDataUrl =
                    "data:image/png;base64," + base64Image;

            return new ImageResponse(
                    imageDataUrl,
                    "Image generated successfully"
            );

        } catch (WebClientResponseException ex) {
            throw new IllegalStateException(
                    "OpenAI image generation failed with HTTP "
                            + ex.getStatusCode()
                            + ". Check your API key, access and billing.",
                    ex
            );
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to generate image. Please try again.",
                    ex
            );
        }
    }
}
