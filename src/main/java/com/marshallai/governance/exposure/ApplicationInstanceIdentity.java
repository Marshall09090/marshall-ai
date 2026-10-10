package com.marshallai.governance.exposure;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Identifies one running MarshallAI application process.
 *
 * A new bean instance represents a new application startup,
 * so every startup receives a fresh UUID.
 */
@Component
public class ApplicationInstanceIdentity {

    private final UUID instanceId;

    public ApplicationInstanceIdentity() {

        this.instanceId =
                UUID.randomUUID();
    }

    public UUID instanceId() {

        return instanceId;
    }
}