package io.consenthub.domain.enums;

public enum LegalBasis {
    STRICTLY_NECESSARY,
    CONSENT,
    LEGITIMATE_INTEREST_OPT_OUT;

    public String humanLabel() {
        return switch (this) {
            case STRICTLY_NECESSARY -> "Strictly Necessary";
            case CONSENT -> "Explicit Consent";
            case LEGITIMATE_INTEREST_OPT_OUT -> "Legitimate Interest (Opt-Out)";
        };
    }
}
