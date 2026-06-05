package com.echoe.backend.service;

import com.echoe.backend.entity.SessionEntity;
import com.echoe.backend.entity.UserEntity;
import com.echoe.backend.repository.SessionRepository;
import com.echoe.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ScheduledJobsService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobsService.class);

    private final IntentionService intentionService;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public ScheduledJobsService(IntentionService intentionService,
                                SessionRepository sessionRepository,
                                UserRepository userRepository) {
        this.intentionService = intentionService;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Daily 5am IST — generate personalized intentions for active users.
     */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Kolkata")
    public void generateDailyIntentions() {
        log.info("Running daily intention generation job");
        intentionService.generateForAllActiveUsers();
        log.info("Daily intention generation complete");
    }

    /**
     * Hourly — soft-delete expired vault sessions.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredVaultSessions() {
        log.info("Running vault session cleanup");
        List<SessionEntity> expired = sessionRepository
                .findByVaultExpiresAtBeforeAndDeletedAtIsNull(Instant.now());

        for (SessionEntity session : expired) {
            session.setDeletedAt(Instant.now());
        }
        sessionRepository.saveAll(expired);
        log.info("Cleaned up {} expired vault sessions", expired.size());
    }

    /**
     * Every 6 days — lightweight DB query to prevent Supabase free tier auto-pause.
     * Supabase pauses after 7 days of inactivity; this keeps it alive.
     */
    @Scheduled(cron = "0 0 3 */6 * *")   // 3am UTC every 6 days
    public void keepSupabaseAlive() {
        long count = userRepository.count();
        log.info("Supabase keep-alive ping — total users: {}", count);
    }

    /**
     * Sunday midnight IST — reset weekly session counters for free-tier users.
     */
    @Scheduled(cron = "0 0 0 * * SUN", zone = "Asia/Kolkata")
    @Transactional
    public void resetWeeklySessionCounters() {
        log.info("Running weekly session counter reset");
        List<UserEntity> users = userRepository.findAll();
        Instant now = Instant.now();

        int resetCount = 0;
        for (UserEntity user : users) {
            if (user.getSessionsThisWeek() > 0) {
                user.setSessionsThisWeek(0);
                user.setWeekResetAt(now);
                resetCount++;
            }
        }
        userRepository.saveAll(users);
        log.info("Reset weekly session counters for {} users", resetCount);
    }
}
