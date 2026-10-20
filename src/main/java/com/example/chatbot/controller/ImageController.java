
package com.example.chatbot.controller;

import com.example.chatbot.dto.ImageRequest;
import com.example.chatbot.dto.ImageResponse;
import com.example.chatbot.service.ImageService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/multimodal")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/image")
    public ResponseEntity<ImageResponse> generateImage(
            @Valid @RequestBody ImageRequest request) {

        ImageResponse response =
                imageService.generateImage(request.prompt());

        return ResponseEntity.ok(response);
    }
}
