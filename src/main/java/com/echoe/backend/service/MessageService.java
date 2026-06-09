package com.echoe.backend.service;

import com.echoe.backend.config.RateLimitProperties;
import com.echoe.backend.dto.ai.EchoResponse;
import com.echoe.backend.dto.chat.ChatMessage;
import com.echoe.backend.dto.message.SendTextRequest;
import com.echoe.backend.dto.message.SendTextResponse;
import com.echoe.backend.dto.safety.CrisisResource;
import com.echoe.backend.dto.safety.EmergencyContact;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final SafetyService safetyService;
    private final CrisisResourceProvider crisisResourceProvider;
    private final VoiceService voiceService;
    private final ToneEngine toneEngine;
    private final RateLimitProperties rateLimitProperties;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public MessageService(MessageRepository messageRepository,
                          SessionRepository sessionRepository,
                          UserRepository userRepository,
                          AIService aiService,
                          SafetyService safetyService,
                          CrisisResourceProvider crisisResourceProvider,
                          VoiceService voiceService,
                          ToneEngine toneEngine,
                          RateLimitProperties rateLimitProperties,
                          tools.jackson.databind.ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
        this.safetyService = safetyService;
        this.crisisResourceProvider = crisisResourceProvider;
        this.voiceService = voiceService;
        this.toneEngine = toneEngine;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SendTextResponse processTextMessage(UUID userId, UUID sessionId,
                                               SendTextRequest request) {
        // 0. Check per-session message limit
        enforceMessageLimit(sessionId);

        // 1. Build conversation history from DB BEFORE saving new message
        List<ChatMessage> history = buildHistory(sessionId);

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
        EmergencyContact emergencyContact = null;

        if (crisisDetected) {
            SessionEntity session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null && !session.isCrisisFlagged()) {
                session.setCrisisFlagged(true);
                sessionRepository.save(session);
            }
            emergencyContact = resolveEmergencyContact(userId);
        }

        List<CrisisResource> resources = crisisDetected
                ? crisisResourceProvider.getResources()
                : List.of();

        // 7. Synthesize TTS for the echo response (same as voice mode)
        String audioBase64 = null;
        try {
            String voicePreference = userRepository.findById(userId)
                    .map(UserEntity::getVoicePreference)
                    .orElse("female");
            String language = sessionRepository.findById(sessionId)
                    .map(s -> s.getLanguage() != null ? s.getLanguage() : "en")
                    .orElse("en");
            ToneConfig toneConfig = toneEngine.resolve(echoResponse.suggestedTone(), voicePreference);
            byte[] ttsAudio = voiceService.synthesize(echoContent, toneConfig, language);
            audioBase64 = Base64.getEncoder().encodeToString(ttsAudio);
        } catch (Exception e) {
            // TTS failure is non-fatal — text response still delivered
            log.warn("TTS synthesis failed for text message, returning text only: {}", e.getMessage());
        }

        return new SendTextResponse(
                echoMessage.getId(),
                echoResponse,
                crisisDetected,
                resources,
                !echoResponse.sessionShouldEnd(),
                emergencyContact,
                audioBase64
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
        List<ChatMessage> history = buildHistory(sessionId);

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
        EmergencyContact emergencyContact = null;

        if (crisisDetected) {
            SessionEntity session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null && !session.isCrisisFlagged()) {
                session.setCrisisFlagged(true);
                sessionRepository.save(session);
            }
            emergencyContact = resolveEmergencyContact(userId);
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
                !echoResponse.sessionShouldEnd(),
                emergencyContact
        );
    }

    private List<ChatMessage> buildHistory(UUID sessionId) {
        List<MessageEntity> recentMessages =
                messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        List<MessageEntity> chronological = new ArrayList<>(recentMessages);
        Collections.reverse(chronological);

        return chronological.stream()
                .map(m -> {
                    if ("echo".equals(m.getRole())) {
                        // Reconstruct JSON so DeepSeek sees consistent format in history
                        return new ChatMessage("echo", toEchoJson(m));
                    }
                    return new ChatMessage("user", m.getContent());
                })
                .toList();
    }

    private String toEchoJson(MessageEntity m) {
        String content = m.getContent();
        String reflection = content;
        String question = "";
        int split = content.indexOf("\n\n");
        if (split >= 0) {
            reflection = content.substring(0, split);
            question = content.substring(split + 2);
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("reflection", reflection);
        json.put("question", question);
        json.put("detected_emotion", m.getDetectedEmotion() != null ? m.getDetectedEmotion() : "other");
        json.put("intensity", m.getEmotionIntensity() != null ? m.getEmotionIntensity() : 0.3);
        json.put("suggested_tone", m.getToneUsed() != null ? m.getToneUsed() : "warm_curious");
        json.put("crisis_flag", false);
        json.put("session_should_end", false);

        try {
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            return content; // fallback to plain text if serialization fails
        }
    }

    /**
     * Increments the user's total crisis detection counter and returns their emergency
     * contact once the threshold (3 individual detections) is reached.
     *
     * Counter increments every time crisis is detected — even in the same session —
     * so "3 crisis messages" triggers the contact on the 3rd message, not the 3rd session.
     */
    @Transactional
    private EmergencyContact resolveEmergencyContact(UUID userId) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;

        user.incrementCrisisDetectionCount();
        userRepository.save(user);

        if (user.getCrisisDetectionCount() < 3) return null;
        if (user.getEmergencyContactPhone() == null
                || user.getEmergencyContactPhone().isBlank()) return null;

        return new EmergencyContact(
                user.getEmergencyContactName(),
                user.getEmergencyContactPhone()
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
