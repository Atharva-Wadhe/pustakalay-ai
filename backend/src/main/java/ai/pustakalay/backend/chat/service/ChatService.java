package ai.pustakalay.backend.chat.service;

import ai.pustakalay.backend.books.entity.Document;
import ai.pustakalay.backend.books.entity.DocumentEvent;
import ai.pustakalay.backend.books.repository.DocumentRepository;
import ai.pustakalay.backend.books.service.BookService;
import ai.pustakalay.backend.chat.entity.ChatMessage;
import ai.pustakalay.backend.chat.entity.ChatSession;
import ai.pustakalay.backend.chat.repository.ChatMessageRepository;
import ai.pustakalay.backend.chat.repository.ChatSessionRepository;
import ai.pustakalay.backend.retrieval.entity.RetrievalLog;
import ai.pustakalay.backend.retrieval.repository.RetrievalLogRepository;
import ai.pustakalay.backend.retrieval.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RetrievalService retrievalService;
    private final RetrievalLogRepository retrievalLogRepository;
    private final DocumentRepository documentRepository;
    private final BookService bookService;
    private final ChatModel chatModel;

    public List<ChatSession> listSessions() {
        return chatSessionRepository.findAllByOrderByUpdatedAtDesc();
    }

    public ChatSession createSession(String title) {
        ChatSession session = ChatSession.builder()
                .title(title != null ? title : "New Conversation")
                .build();
        return chatSessionRepository.save(session);
    }

    public List<ChatMessage> getMessages(UUID sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
        log.info("Deleted chat session: {}", sessionId);
    }

    @Transactional
    public ChatResponseDto sendMessage(UUID sessionId, String userMessage, UUID documentId, String category) {
        // 1. Save user message
        ChatMessage userMsg = ChatMessage.builder()
                .sessionId(sessionId)
                .role("user")
                .message(userMessage)
                .build();
        chatMessageRepository.save(userMsg);

        // 2. Retrieve context
        // Default topK = 5
        List<RetrievalService.RetrievedContext> contexts = retrievalService.retrieveContext(userMessage, documentId,
                category, 5);

        // 3. Build prompt with context and chat history
        List<ChatMessage> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        // Exclude the last user message from history since we append it manually
        if (!history.isEmpty()) {
            history.remove(history.size() - 1);
        }

        String systemPrompt = buildSystemPrompt(contexts);
        String fullPrompt = buildFullPrompt(systemPrompt, history, userMessage);

        // 4. Call LLM
        log.info("Calling LLM for session: {}", sessionId);
        long llmStartTime = System.currentTimeMillis();
        ChatResponse response = chatModel.call(new Prompt(fullPrompt));
        long generationTime = System.currentTimeMillis() - llmStartTime;

        String assistantMessage = response.getResult().getOutput().getContent();
        log.info("LLM responded in {} ms", generationTime);

        // 5. Save assistant message
        ChatMessage assistantMsg = ChatMessage.builder()
                .sessionId(sessionId)
                .role("assistant")
                .message(assistantMessage)
                .build();
        chatMessageRepository.save(assistantMsg);

        // Update chat session updatedAt time
        ChatSession session = chatSessionRepository.findById(sessionId).orElse(null);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionRepository.save(session);
        }

        // 6. Update Retrieval Log with generation latency
        if (!contexts.isEmpty()) {
            UUID retrievalLogId = contexts.get(0).getRetrievalLogId();
            RetrievalLog logEntry = retrievalLogRepository.findById(retrievalLogId).orElse(null);
            if (logEntry != null) {
                logEntry.setMessageId(assistantMsg.getId());
                logEntry.setGenerationMs((int) generationTime);
                // Estimate tokens (words * 1.3)
                int promptTokens = (int) Math.ceil(fullPrompt.split("\\s+").length * 1.3);
                int completionTokens = (int) Math.ceil(assistantMessage.split("\\s+").length * 1.3);
                logEntry.setTokens(promptTokens + completionTokens);
                retrievalLogRepository.save(logEntry);
            }
        }

        // 7. Log CHAT_REFERENCED events for cited documents
        Set<UUID> citedDocIds = new HashSet<>();
        for (RetrievalService.RetrievedContext ctx : contexts) {
            // Find document ID by title or search DB
            Optional<Document> docOpt = documentRepository.findAll().stream()
                    .filter(d -> d.getTitle().equals(ctx.getDocumentTitle()))
                    .findFirst();
            docOpt.ifPresent(document -> {
                if (citedDocIds.add(document.getId())) {
                    bookService.logEvent(document.getId(), DocumentEvent.EventType.CHAT_REFERENCED,
                            Map.of("sessionId", sessionId, "query", userMessage));
                }
            });
        }

        return ChatResponseDto.builder()
                .message(assistantMsg)
                .citations(contexts)
                .build();
    }

    private String buildSystemPrompt(List<RetrievalService.RetrievedContext> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Pustakalay.ai, a professional and precise Personal Knowledge Platform assistant.\n");
        sb.append("Use the following retrieved context chunks from the user's library to answer the question.\n");
        sb.append(
                "If the context does not contain the answer, state that you cannot find the answer in the library, but do not make up information.\n");
        sb.append("Always cite the source document title, chapter, and page numbers when referencing facts.\n\n");
        sb.append("Retrieved Context:\n");

        if (contexts.isEmpty()) {
            sb.append("No relevant context found in the library.\n");
        } else {
            for (RetrievalService.RetrievedContext ctx : contexts) {
                sb.append(String.format(
                        "--- \nSource: %s\nChapter: %s | Section: %s\nPages: %d-%d\nContent:\n%s\n---\n\n",
                        ctx.getDocumentTitle(), ctx.getChapter(), ctx.getSection(), ctx.getPageStart(),
                        ctx.getPageEnd(), ctx.getText()));
            }
        }
        return sb.toString();
    }

    private String buildFullPrompt(String systemPrompt, List<ChatMessage> history, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt);
        sb.append("\nConversation History:\n");

        for (ChatMessage msg : history) {
            if ("user".equalsIgnoreCase(msg.getRole())) {
                sb.append("User: ").append(msg.getMessage()).append("\n");
            } else if ("assistant".equalsIgnoreCase(msg.getRole())) {
                sb.append("Assistant: ").append(msg.getMessage()).append("\n");
            }
        }

        sb.append("User: ").append(userMessage).append("\n");
        sb.append("Assistant: ");
        return sb.toString();
    }

    @lombok.Value
    @lombok.Builder
    public static class ChatResponseDto {
        ChatMessage message;
        List<RetrievalService.RetrievedContext> citations;
    }
}
