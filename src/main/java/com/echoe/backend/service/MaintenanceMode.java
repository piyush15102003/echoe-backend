package com.echoe.backend.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory maintenance flag.
 * Shared between MaintenanceFilter and AdminController.
 * Resets to false on restart (intentional — redeploys clear maintenance mode automatically).
 */
@Component
public class MaintenanceMode {

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
