package io.consenthub.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255, message = "Email must be 255 characters or fewer")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Must be at least 8 characters and at most 128 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
        String fullName,

        String region,

        String anonymousSubjectKey
) {
    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", password=[REDACTED], region=" + region + "]";
    }
}
