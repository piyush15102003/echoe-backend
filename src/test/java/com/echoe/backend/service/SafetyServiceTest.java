package com.echoe.backend.service;

import com.echoe.backend.dto.safety.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafetyServiceTest {

    private SafetyService safetyService;

    @BeforeEach
    void setUp() throws Exception {
        safetyService = new SafetyService();
        // Trigger @PostConstruct manually
        safetyService.loadKeywords();
    }

    @Test
    void detectsExplicitSuicideIntent() {
        SafetyResult result = safetyService.check("I want to kill myself");
        assertTrue(result.crisis());
    }

    @Test
    void detectsSuicideKeyword() {
        SafetyResult result = safetyService.check("I've been thinking about suicide");
        assertTrue(result.crisis());
    }

    @Test
    void detectsSelfHarm() {
        SafetyResult result = safetyService.check("I want to cut myself again");
        assertTrue(result.crisis());
    }

    @Test
    void detectsEndMyLife() {
        SafetyResult result = safetyService.check("I just want to end my life");
        assertTrue(result.crisis());
    }

    @Test
    void detectsHindiCrisisKeyword() {
        SafetyResult result = safetyService.check("मुझे मरना चाहता हूं");
        assertTrue(result.crisis());
    }

    @Test
    void detectsHinglishCrisisKeyword() {
        SafetyResult result = safetyService.check("mujhe khudkhushi karni hai");
        assertTrue(result.crisis());
    }

    @Test
    void safeMessageReturnsNoCrisis() {
        SafetyResult result = safetyService.check("I have been feeling really overwhelmed lately");
        assertFalse(result.crisis());
    }

    @Test
    void emptyMessageReturnsNoCrisis() {
        SafetyResult result = safetyService.check("");
        assertFalse(result.crisis());
    }

    @Test
    void nullMessageReturnsNoCrisis() {
        SafetyResult result = safetyService.check(null);
        assertFalse(result.crisis());
    }

    @Test
    void caseInsensitiveDetection() {
        SafetyResult result = safetyService.check("I WANT TO KILL MYSELF");
        assertTrue(result.crisis());
    }
}
