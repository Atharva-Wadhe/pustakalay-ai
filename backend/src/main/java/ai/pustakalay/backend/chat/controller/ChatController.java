package ai.pustakalay.backend.chat.controller;

import ai.pustakalay.backend.chat.entity.ChatMessage;
import ai.pustakalay.backend.chat.entity.ChatSession;
import ai.pustakalay.backend.chat.service.ChatService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> getSessions() {
        return ResponseEntity.ok(chatService.listSessions());
    }

    @PostMapping("/sessions")
    public ResponseEntity<ChatSession> createSession(@RequestBody(required = false) CreateSessionRequest request) {
        String title = request != null ? request.getTitle() : "New Conversation";
        return ResponseEntity.ok(chatService.createSession(title));
    }

    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(@PathVariable UUID id) {
        return ResponseEntity.ok(chatService.getMessages(id));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        chatService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<ChatService.ChatResponseDto> sendMessage(
            @PathVariable UUID id,
            @RequestBody SendMessageRequest request) {
        ChatService.ChatResponseDto response = chatService.sendMessage(
                id,
                request.getMessage(),
                request.getDocumentId(),
                request.getCategory());
        return ResponseEntity.ok(response);
    }

    @Data
    public static class CreateSessionRequest {
        private String title;
    }

    @Data
    public static class SendMessageRequest {
        private String message;
        private UUID documentId;
        private String category;
    }
}
