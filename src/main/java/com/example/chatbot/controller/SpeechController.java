
package com.example.chatbot.controller;

import com.example.chatbot.dto.TranscriptionResponse;
import com.example.chatbot.service.SpeechToTextService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/multimodal")
public class SpeechController {

    private final SpeechToTextService speechToTextService;

    public SpeechController(SpeechToTextService speechToTextService) {
        this.speechToTextService = speechToTextService;
    }

    @PostMapping(
            value = "/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TranscriptionResponse> transcribe(
            @RequestParam("file") MultipartFile file) {

        TranscriptionResponse response =
                speechToTextService.transcribe(file);

        return ResponseEntity.ok(response);
    }
}
