package com.echoe.backend.dto.safety;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Included in message responses when the user has hit the crisis threshold (3 flagged sessions)
 * and has an emergency contact saved. The Flutter app uses this to show a "call / text someone
 * who cares about you" prompt.
 */
public record EmergencyContact(
        @JsonProperty("name") String name,
        @JsonProperty("phone") String phone
) {}
