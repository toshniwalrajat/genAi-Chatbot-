
package com.example.chatbot.repository;

import com.example.chatbot.model.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatConversationRepository
        extends JpaRepository<ChatConversation, String> {

    List<ChatConversation> findAllByOrderByUpdatedAtDesc();
}
