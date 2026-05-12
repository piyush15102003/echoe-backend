package com.echoe.backend.service;

import com.echoe.backend.config.RateLimitProperties;
import com.echoe.backend.dto.ai.EchoResponse;
import com.echoe.backend.dto.chat.ChatMessage;
import com.echoe.backend.dto.message.SendTextRequest;
import com.echoe.backend.dto.message.SendTextResponse;
import com.echoe.backend.dto.safety.CrisisResource;
import com.echoe.backend.dto.safety.SafetyResult;
import com.echoe.backend.dto.voice.SendVoiceResponse;
import com.echoe.backend.dto.voice.ToneConfig;
import com.echoe.backend.entity.MessageEntity;
import com.echoe.backend.entity.SessionEntity;
import com.echoe.backend.entity.UserEntity;
import com.echoe.backend.exception.RateLimitException;
import com.echoe.backend.repository.MessageRepository;
import com.echoe.backend.repository.SessionRepository;
import com.echoe.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final SafetyService safetyService;
    private final CrisisResourceProvider crisisResourceProvider;
    private final VoiceService voiceService;
    private final ToneEngine toneEngine;
    private final RateLimitProperties rateLimitProperties;

    public MessageService(MessageRepository messageRepository,
                          SessionRepository sessionRepository,
                          UserRepository userRepository,
                          AIService aiService,
                          SafetyService safetyService,
                          CrisisResourceProvider crisisResourceProvider,
                          VoiceService voiceService,
                          ToneEngine toneEngine,
                          RateLimitProperties rateLimitProperties) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
        this.safetyService = safetyService;
        this.crisisResourceProvider = crisisResourceProvider;
        this.voiceService = voiceService;
        this.toneEngine = toneEngine;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Transactional
    public SendTextResponse processTextMessage(UUID userId, UUID sessionId,
                                               SendTextRequest request) {
        // 0. Check per-session message limit
        enforceMessageLimit(sessionId);

        // 1. Build conversation history from DB BEFORE saving new message
        List<MessageEntity> recentMessages =
                messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        List<MessageEntity> chronological = new ArrayList<>(recentMessages);
        Collections.reverse(chronological);

        List<ChatMessage> history = chronological.stream()
                .map(m -> new ChatMessage(
                        "echo".equals(m.getRole()) ? "echo" : "user",
                        m.getContent()
                ))
                .toList();

        // 2. Save user message
        MessageEntity userMessage = new MessageEntity(sessionId, "user", request.content());
        messageRepository.save(userMessage);

        // 3. Safety check
        SafetyResult safetyResult = safetyService.check(request.content());

        // 4. Call AI with history
        EchoResponse echoResponse = aiService.chat(request.content(), history);

        // 5. Save echo response message
        String echoContent = echoResponse.reflection();
        if (echoResponse.question() != null && !echoResponse.question().isBlank()) {
            echoContent += "\n\n" + echoResponse.question();
        }

        MessageEntity echoMessage = new MessageEntity(sessionId, "echo", echoContent);
        echoMessage.setDetectedEmotion(echoResponse.detectedEmotion());
        echoMessage.setEmotionIntensity(echoResponse.intensity());
        echoMessage.setToneUsed(echoResponse.suggestedTone());
        messageRepository.save(echoMessage);

        // 6. Crisis detection (defense in depth)
        boolean crisisDetected = safetyResult.crisis() || echoResponse.crisisFlag();

        if (crisisDetected) {
            SessionEntity session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null && !session.isCrisisFlagged()) {
                session.setCrisisFlagged(true);
                sessionRepository.save(session);
            }
        }

        List<CrisisResource> resources = crisisDetected
                ? crisisResourceProvider.getResources()
                : List.of();

        return new SendTextResponse(
                echoMessage.getId(),
                echoResponse,
                crisisDetected,
                resources,
                !echoResponse.sessionShouldEnd()
        );
    }

    @Transactional
    public SendVoiceResponse processVoiceMessage(UUID userId, UUID sessionId,
                                                  byte[] audio, String language) {
        // 0. Check per-session message limit
        enforceMessageLimit(sessionId);

        // 1. Transcribe audio to text
        String transcribedText = voiceService.transcribe(audio, language);

        // 2. Build conversation history from DB BEFORE saving new message
        List<MessageEntity> recentMessages =
                messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        List<MessageEntity> chronological = new ArrayList<>(recentMessages);
        Collections.reverse(chronological);

        List<ChatMessage> history = chronological.stream()
                .map(m -> new ChatMessage(
                        "echo".equals(m.getRole()) ? "echo" : "user",
                        m.getContent()
                ))
                .toList();

        // 3. Save user message
        MessageEntity userMessage = new MessageEntity(sessionId, "user", transcribedText);
        messageRepository.save(userMessage);

        // 4. Safety check
        SafetyResult safetyResult = safetyService.check(transcribedText);

        // 5. Call AI with history
        EchoResponse echoResponse = aiService.chat(transcribedText, history);

        // 6. Save echo response message
        String echoContent = echoResponse.reflection();
        if (echoResponse.question() != null && !echoResponse.question().isBlank()) {
            echoContent += "\n\n" + echoResponse.question();
        }

        MessageEntity echoMessage = new MessageEntity(sessionId, "echo", echoContent);
        echoMessage.setDetectedEmotion(echoResponse.detectedEmotion());
        echoMessage.setEmotionIntensity(echoResponse.intensity());
        echoMessage.setToneUsed(echoResponse.suggestedTone());

        // 7. Resolve tone config and synthesize speech
        String voicePreference = userRepository.findById(userId)
                .map(UserEntity::getVoicePreference)
                .orElse("female");
        ToneConfig toneConfig = toneEngine.resolve(echoResponse.suggestedTone(), voicePreference);
        byte[] ttsAudio = voiceService.synthesize(echoContent, toneConfig, language);
        String audioBase64 = Base64.getEncoder().encodeToString(ttsAudio);

        messageRepository.save(echoMessage);

        // 8. Crisis detection (defense in depth)
        boolean crisisDetected = safetyResult.crisis() || echoResponse.crisisFlag();

        if (crisisDetected) {
            SessionEntity session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null && !session.isCrisisFlagged()) {
                session.setCrisisFlagged(true);
                sessionRepository.save(session);
            }
        }

        List<CrisisResource> resources = crisisDetected
                ? crisisResourceProvider.getResources()
                : List.of();

        return new SendVoiceResponse(
                echoMessage.getId(),
                transcribedText,
                echoResponse,
                audioBase64,
                crisisDetected,
                resources,
                !echoResponse.sessionShouldEnd()
        );
    }

    private void enforceMessageLimit(UUID sessionId) {
        long userMessageCount = messageRepository.countBySessionIdAndRole(sessionId, "user");
        if (userMessageCount >= rateLimitProperties.messagesPerSession()) {
            throw new RateLimitException("Message limit reached: "
                    + rateLimitProperties.messagesPerSession() + " messages per session");
        }
    }
}
