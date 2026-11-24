package io.consenthub.sdui;

import java.util.List;

/**
 * Thrown when a server-composed or inbound Server-Driven UI (SDUI) screen fails
 * structural, allowlist, nesting depth, or URI security validation against
 * {@code sdui-schema.json}.
 */
public class SduiValidationException extends RuntimeException {

    private final List<String> violations;

    public SduiValidationException(String message) {
        super(message);
        this.violations = List.of(message);
    }

    public SduiValidationException(String message, List<String> violations) {
        super(message + ": " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
