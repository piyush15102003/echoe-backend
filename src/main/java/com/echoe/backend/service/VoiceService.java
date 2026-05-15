package com.echoe.backend.service;

import com.echoe.backend.config.SarvamProperties;
import com.echoe.backend.dto.voice.ToneConfig;
import com.echoe.backend.exception.AIServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    private final WebClient sarvamWebClient;
    private final SarvamProperties props;

    public VoiceService(WebClient sarvamWebClient, SarvamProperties props) {
        this.sarvamWebClient = sarvamWebClient;
        this.props = props;
    }

    /**
     * Normalize language codes for Sarvam API (e.g. "en" → "en-IN").
     */
    private String normalizeLangCode(String language) {
        if (language == null || language.isBlank()) {
            return "en-IN";
        }
        // Already a full Sarvam code like "hi-IN", "en-IN"
        if (language.contains("-")) {
            return language;
        }
        // Map short codes to Sarvam codes
        return switch (language.toLowerCase()) {
            case "en" -> "en-IN";
            case "hi" -> "hi-IN";
            case "bn" -> "bn-IN";
            case "kn" -> "kn-IN";
            case "ml" -> "ml-IN";
            case "mr" -> "mr-IN";
            case "ta" -> "ta-IN";
            case "te" -> "te-IN";
            case "gu" -> "gu-IN";
            case "pa" -> "pa-IN";
            case "ur" -> "ur-IN";
            default -> "en-IN";
        };
    }

    /**
     * Transcribe audio bytes to text using Sarvam STT API.
     */
    public String transcribe(byte[] audio, String language) {
        String sarvamLang = normalizeLangCode(language);
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "audio.wav";
            }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);
        builder.part("model", props.sttModel());
        builder.part("mode", "transcribe");
        builder.part("language_code", sarvamLang);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = sarvamWebClient.post()
                    .uri("/speech-to-text")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("transcript")) {
                throw new AIServiceException("Empty STT response from Sarvam API");
            }

            String transcript = (String) response.get("transcript");
            log.debug("STT transcript: {} chars", transcript.length());
            return transcript;

        } catch (WebClientResponseException ex) {
            log.error("Sarvam STT error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AIServiceException("Sarvam STT API returned " + ex.getStatusCode(), ex);
        } catch (AIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AIServiceException("Failed to transcribe audio", ex);
        }
    }

    /**
     * Synthesize text to speech using Sarvam TTS API.
     * Returns raw MP3 audio bytes.
     */
    public byte[] synthesize(String text, ToneConfig config, String language) {
        String sarvamLang = normalizeLangCode(language);
        Map<String, Object> body = Map.of(
                "text", text,
                "target_language_code", sarvamLang,
                "model", props.ttsModel(),
                "speaker", config.speaker(),
                "pace", config.pace(),
                "speech_sample_rate", 22050
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = sarvamWebClient.post()
                    .uri("/text-to-speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("audios")) {
                throw new AIServiceException("Empty TTS response from Sarvam API");
            }

            @SuppressWarnings("unchecked")
            List<String> audios = (List<String>) response.get("audios");
            if (audios.isEmpty()) {
                throw new AIServiceException("No audio in Sarvam TTS response");
            }

            byte[] audioBytes = Base64.getDecoder().decode(audios.getFirst());
            log.debug("TTS audio: {} bytes", audioBytes.length);
            return audioBytes;

        } catch (WebClientResponseException ex) {
            log.error("Sarvam TTS error: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AIServiceException("Sarvam TTS API returned " + ex.getStatusCode(), ex);
        } catch (AIServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AIServiceException("Failed to synthesize speech", ex);
        }
    }
}
