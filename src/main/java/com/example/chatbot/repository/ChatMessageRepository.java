
package com.example.chatbot.repository;

import com.example.chatbot.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage>
    findByConversation_IdOrderByCreatedAtAsc(String conversationId);

    List<ChatMessage>
    findTop12ByConversation_IdOrderByCreatedAtDesc(String conversationId);

    void deleteByConversation_Id(String conversationId);
}
