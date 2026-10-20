
package com.example.chatbot.service;

import com.example.chatbot.dto.TranscriptionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Service
public class SpeechToTextService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("mp3", "wav", "m4a");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final boolean mockMode;

    public SpeechToTextService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.transcription-model:gpt-transcribe}") String model,
            @Value("${speech.mock-mode:true}") boolean mockMode) {

        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .build();

        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.mockMode = mockMode;
    }

    public TranscriptionResponse transcribe(MultipartFile file) {

        validateFile(file);

        // Mock mode works without an OpenAI API key.
        if (mockMode) {
            return new TranscriptionResponse(
                    "Add Java class tomorrow");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is missing. Set OPENAI_API_KEY.");
        }

        try {
            byte[] audioBytes = file.getBytes();

            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename == null
                    ? "audio." + getExtension(file)
                    : originalFilename;

            ByteArrayResource audioResource =
                    new ByteArrayResource(audioBytes) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    };

            MultipartBodyBuilder bodyBuilder =
                    new MultipartBodyBuilder();

            bodyBuilder.part("file", audioResource)
                    .contentType(getMediaType(file));

            bodyBuilder.part("model", model);

            String responseBody = webClient.post()
                    .uri("/audio/transcriptions")
                    .headers(headers ->
                            headers.setBearerAuth(apiKey))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(
                            bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException(
                        "OpenAI returned an empty transcription response.");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("text").asText("");

            if (text.isBlank()) {
                throw new IllegalStateException(
                        "No speech could be transcribed from this file.");
            }

            return new TranscriptionResponse(text);

        } catch (WebClientResponseException ex) {
            throw new IllegalStateException(
                    "OpenAI transcription failed with HTTP "
                            + ex.getStatusCode()
                            + ". Check your API key and model access.",
                    ex);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to read the uploaded audio file.", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to transcribe the audio file.", ex);
        }
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Audio file cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Audio file must not exceed 10 MB.");
        }

        String extension = getExtension(file);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported audio format. Use MP3, WAV, or M4A.");
        }
    }

    private String getExtension(MultipartFile file) {

        String filename = file.getOriginalFilename();

        if (filename == null || !filename.contains(".")) {
            return "";
        }

        return filename.substring(filename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
    }

    private MediaType getMediaType(MultipartFile file) {

        return switch (getExtension(file)) {
            case "mp3" -> MediaType.parseMediaType("audio/mpeg");
            case "wav" -> MediaType.parseMediaType("audio/wav");
            case "m4a" -> MediaType.parseMediaType("audio/mp4");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
