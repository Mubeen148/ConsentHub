package io.consenthub.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "experiment_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uq_experiment_subject", columnNames = {"experiment_id", "subject_key"})
)
public class ExperimentAssignmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "experiment_id", nullable = false, updatable = false)
    private UUID experimentId;

    @Column(name = "subject_key", nullable = false, updatable = false, length = 128)
    private String subjectKey;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "variant_key", nullable = false, length = 64)
    private String variantKey;

    @Column(name = "converted", nullable = false)
    private boolean converted = false;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.assignedAt == null) {
            this.assignedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(UUID experimentId) {
        this.experimentId = experimentId;
    }

    public String getSubjectKey() {
        return subjectKey;
    }

    public void setSubjectKey(String subjectKey) {
        this.subjectKey = subjectKey;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getVariantKey() {
        return variantKey;
    }

    public void setVariantKey(String variantKey) {
        this.variantKey = variantKey;
    }

    public boolean isConverted() {
        return converted;
    }

    public void setConverted(boolean converted) {
        this.converted = converted;
    }

    public Instant getConvertedAt() {
        return convertedAt;
    }

    public void setConvertedAt(Instant convertedAt) {
        this.convertedAt = convertedAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }
}
