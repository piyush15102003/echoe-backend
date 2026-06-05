package com.echoe.backend.controller;

import com.echoe.backend.repository.MessageRepository;
import com.echoe.backend.repository.SessionRepository;
import com.echoe.backend.repository.UserRepository;
import com.echoe.backend.service.MaintenanceMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Admin-only endpoints — protected by X-Admin-Key header (not JWT).
 * Never expose ADMIN_SECRET_KEY client-side.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Value("${admin.secret-key:not-configured}")
    private String adminSecretKey;

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final MaintenanceMode maintenanceMode;

    public AdminController(UserRepository userRepository,
                           SessionRepository sessionRepository,
                           MessageRepository messageRepository,
                           MaintenanceMode maintenanceMode) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.maintenanceMode = maintenanceMode;
    }

    // ── Auth guard ────────────────────────────────────────────────────────────

    private boolean isAuthorized(String key) {
        return adminSecretKey != null && adminSecretKey.equals(key);
    }

    // ── GET /admin/metrics ────────────────────────────────────────────────────

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (!isAuthorized(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized"));
        }

        Instant now = Instant.now();
        Instant startOfToday = now.truncatedTo(ChronoUnit.DAYS);
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        Instant startOfWeek = now.minus(7, ChronoUnit.DAYS); // rolling 7-day window

        long totalUsers = userRepository.count();
        long newToday = userRepository.countByCreatedAtAfter(startOfToday);
        long active7d = userRepository.countByLastActiveAtAfter(sevenDaysAgo);

        long totalSessions = sessionRepository.count();
        long sessionsToday = sessionRepository.countByStartedAtAfter(startOfToday);
        long sessionsThisWeek = sessionRepository.countByStartedAtAfter(startOfWeek);
        long crisisEvents = sessionRepository.countByCrisisFlaggedTrue();

        long totalMessages = messageRepository.count();

        return ResponseEntity.ok(Map.of(
            "users", Map.of(
                "total", totalUsers,
                "new_today", newToday,
                "active_7d", active7d
            ),
            "sessions", Map.of(
                "total", totalSessions,
                "today", sessionsToday,
                "this_week", sessionsThisWeek
            ),
            "messages", Map.of(
                "total", totalMessages
            ),
            "crisis_events", Map.of(
                "total", crisisEvents
            ),
            "maintenance_mode", maintenanceMode.isEnabled()
        ));
    }

    // ── POST /admin/maintenance ───────────────────────────────────────────────

    @PostMapping("/maintenance")
    public ResponseEntity<?> setMaintenance(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody Map<String, Boolean> body) {

        if (!isAuthorized(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "unauthorized"));
        }

        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "missing field: enabled"));
        }

        maintenanceMode.setEnabled(enabled);

        return ResponseEntity.ok(Map.of(
            "maintenance_mode", maintenanceMode.isEnabled(),
            "applied_at", Instant.now().toString()
        ));
    }
}
