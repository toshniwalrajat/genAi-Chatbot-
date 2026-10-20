
package com.example.chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_conversations")
public class ChatConversation {

    @Id
    private String id;

    private String title;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ChatConversation() {
    }

    @PrePersist
    public void onCreate() {
        // Generate a unique ID for a new conversation.
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
