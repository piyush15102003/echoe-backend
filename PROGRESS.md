# Echoe — Progress Tracker

## Backend Phase 1: AI Chat Core (COMPLETED)
- DeepSeek API integration (migrated from Gemini)
- AIService with system prompt + conversation history
- SafetyService with crisis keyword detection
- CrisisResourceProvider (India helplines)
- TestChatController (`/test/chat` — no auth, dev only)
- DTOs: DeepSeekRequest, DeepSeekResponse, EchoResponse, ChatMessage, ChatRequest, ChatResponse

**Files created:** AIService, SafetyService, CrisisResourceProvider, TestChatController, DeepSeekProperties, WebClientConfig, all AI/chat DTOs, system-prompt.txt, crisis-keywords.txt

---

## Backend Phase 2: Backend Foundations (COMPLETED)
- JPA entities: UserEntity, SessionEntity, MessageEntity, RefreshTokenEntity
- Repositories: UserRepository, SessionRepository, MessageRepository, RefreshTokenRepository
- JWT auth: JwtService (HS256, JJWT), JwtAuthenticationFilter
- AuthService: anonymous user creation (idempotent by device_id), refresh token rotation with theft detection
- SessionService: start/end/getActive session management
- MessageService: text message processing pipeline (history → save → safety → AI → save echo → crisis check)
- SecurityConfig: stateless JWT, permitAll for auth endpoints
- Controllers: AuthController (`/api/v1/auth`), SessionController (`/api/v1/sessions`)
- DTOs: auth (AnonymousAuthRequest, AuthResponse, RefreshRequest, TokenResponse), session (CreateSessionRequest, CreateSessionResponse, EndSessionResponse), message (SendTextRequest, SendTextResponse)
- Exceptions: AuthException, SessionException, updated GlobalExceptionHandler
- Config: JwtProperties, datasource/JPA in application.yml, JJWT dependencies in pom.xml

**DB migration (manual):** refresh_tokens table

---

## Backend Phase 3: Voice Pipeline (COMPLETED)
- SarvamProperties config (`echoe.sarvam` prefix: apiKey, baseUrl, sttModel, ttsModel)
- sarvamWebClient bean in WebClientConfig (auth via `api-subscription-key` header)
- ToneEngine: emotion → TTS params mapping (pace, temperature, speaker)
  - soft_slow, grounded_soften, calm_steady, warm_curious, gentle_light, anchored_direct
  - Speaker: female→"ritu", male→"shubh"
- VoiceService: STT (Sarvam `saaras:v3`) + TTS (Sarvam `bulbul:v3`)
- MessageService.processVoiceMessage(): transcribe → text pipeline → tone resolve → synthesize → return
- SessionController: `POST /{sessionId}/messages/voice` (multipart: audio file + language param)
- DTOs: ToneConfig (pace, temperature, speaker), SendVoiceResponse (messageId, transcribedText, echo, audioBase64, crisisDetected, crisisResources, sessionContinues)
- Multipart config: max 10MB file, 15MB request

**Files created:** SarvamProperties, ToneEngine, VoiceService, ToneConfig, SendVoiceResponse
**Files modified:** WebClientConfig, application.yml, MessageService, SessionController

---

## Backend Phase 4: Session Summary + Vault (COMPLETED)
- Summary generation via DeepSeek on session end (summary-prompt.txt)
  - summaryText, summaryQuote, closingReflection, emotionTags
  - Graceful fallback: session still ends if summary generation fails
- AIService.generateSummary(): builds full conversation text, calls DeepSeek with summary prompt
- SessionService.endSession() enhanced: generates summary + sets vault expiry based on subscription tier (free=7d, premium=30d)
- EndSessionResponse expanded: includes summary_text, summary_quote, closing_reflection, emotion_tags, crisis_flagged
- VaultService: PIN management (SHA-256 hashed), vault enable/disable, session list/detail/delete
- VaultController (`/api/v1/vault`):
  - `GET /settings` — check vault status
  - `POST /settings` — enable/disable vault + set PIN
  - `POST /sessions` — list vault sessions (PIN required)
  - `POST /sessions/{id}` — session detail with messages (PIN required)
  - `POST /sessions/{id}/delete` — soft-delete session (PIN required)
- SessionRepository: added vault queries (ended+not-deleted, by-id+not-deleted)
- DTOs: SummaryResponse, VaultSettingsRequest/Response, VaultAccessRequest, VaultSessionSummary, VaultSessionDetail (with nested MessageItem)

**Files created:** summary-prompt.txt, SummaryResponse, VaultService, VaultController, VaultSettingsRequest, VaultSettingsResponse, VaultAccessRequest, VaultSessionSummary, VaultSessionDetail
**Files modified:** AIService, SessionService, SessionRepository, EndSessionResponse

---

## Backend Phase 5: Guardrails + Rate Limiting (COMPLETED)
- RateLimitProperties config (`echoe.rate-limit` prefix): requestsPerMinute=60, freeSessionsPerWeek=5, messagesPerSession=50, authRequestsPerMinute=10
- RateLimitingFilter (OncePerRequestFilter): IP-based token bucket rate limiting
  - 60 req/min for general endpoints, 10 req/min for auth endpoints
  - Returns 429 with ProblemDetail JSON when exceeded
  - Skips `/test/**` endpoints
- Free tier session quota: enforced in SessionService.startSession()
  - Tracks `sessionsThisWeek` on UserEntity, auto-resets after 7 days
  - Premium users: unlimited sessions
- Per-session message limit: 50 user messages per session
  - Enforced in MessageService before both text and voice processing
- RateLimitException: new exception → 429 Too Many Requests via GlobalExceptionHandler
- SecurityConfig: RateLimitingFilter added to filter chain (before JWT filter)
- MessageRepository: added `countBySessionIdAndRole()` query

**Files created:** RateLimitException, RateLimitProperties, RateLimitingFilter
**Files modified:** SecurityConfig, GlobalExceptionHandler, SessionService, MessageService, MessageRepository, application.yml

---

## Backend Phase 6: Background Jobs & Polish (COMPLETED)
- DailyIntentionEntity: JPA entity for `daily_intentions` table (user_id, date, intention_text, generated_from_emotions, viewed_at)
- DailyIntentionRepository: findByUserIdAndDate, existsByUserIdAndDate
- IntentionService: generates personalized 5-word intentions from recent emotion tags via DeepSeek
  - `getTodayIntention(userId)` — returns today's intention
  - `markViewed(intentionId, userId)` — records when user views intention
  - `generateForAllActiveUsers()` — generates for all users active in last 7 days
- ScheduledJobsService (3 cron jobs):
  - Daily 5am IST — generate intentions for active users
  - Hourly — soft-delete expired vault sessions
  - Sunday midnight IST — reset weekly session counters
- IntentionController (`/api/v1/intention`):
  - `GET /today` — get today's intention (204 if none)
  - `POST /{id}/viewed` — mark intention as viewed
- @EnableScheduling added to EchoeBackendApplication
- SessionRepository: added `findByVaultExpiresAtBeforeAndDeletedAtIsNull()` for vault cleanup
- intention-prompt.txt: DeepSeek prompt template for daily intentions

**DB migration (manual):** daily_intentions table (see 04_DATABASE.md)
**Files created:** DailyIntentionEntity, DailyIntentionRepository, IntentionService, ScheduledJobsService, IntentionController, IntentionResponse, intention-prompt.txt
**Files modified:** EchoeBackendApplication, SessionRepository

---

## Flutter App (COMPLETED)
- Full app skeleton with 25+ Dart files, zero analyze issues
- Core: AppColors, AppTheme (Noto Serif + Inter), SecureStorage, ApiClient (Dio + JWT interceptor), GoRouter
- Onboarding: Language, Voice, Biometric, PIN setup (4 screens)
- Home: Hero text, CTAs (Speak freely / Write it down), BreathingOrb
- Conversation: Chat UI with user/echo message bubbles, text input, crisis overlay, end session
- Summary: Quote card, emotion tags, closing reflection, CTAs
- History: Vault session list with EchoCards
- Breathing: 4-phase guided breathing (in/hold/out/pause), duration picker, timer
- Settings: Voice/Language toggles, Reset Echoe, Crisis Resources
- Shared widgets: EchoCard, EmotionTag, BreathingOrb, MicButton
- Bottom nav: Home / Echoes / Breathe (ShellRoute)

**Build:** `flutter analyze` — 0 issues, `flutter pub get` — 116 dependencies resolved

---

## Remaining
- SSE streaming for real-time chat (chunked text + parallel audio)
- Audio recording integration (record package)
- Voice message sending flow in Flutter
- Reflection screen / Listening Space pre-conversation screen
- Echoe Detail screen (read-only past session)
- Daily Intention card on Home screen
- Vault PIN flow in History (currently hardcoded)
- Production hardening: health check, structured logging, error recovery
