package io.consenthub.domain.entity;

import io.consenthub.domain.enums.ConsentSource;
import io.consenthub.domain.enums.RegionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "consent_records",
        uniqueConstraints = @UniqueConstraint(name = "uq_consent_records_subject_purpose", columnNames = {"subject_key", "purpose_id"})
)
public class ConsentRecordEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "subject_key", nullable = false, length = 128)
    private String subjectKey;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "purpose_id", nullable = false)
    private ConsentPurposeEntity purpose;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "policy_version_id", nullable = false)
    private PolicyVersionEntity policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", nullable = false, length = 20)
    private RegionCode regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private ConsentSource source;

    @Column(name = "experiment_id")
    private UUID experimentId;

    @Column(name = "variant_key", length = 64)
    private String variantKey;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getSubjectKey() {
        return subjectKey;
    }

    public void setSubjectKey(String subjectKey) {
        this.subjectKey = subjectKey;
    }

    public ConsentPurposeEntity getPurpose() {
        return purpose;
    }

    public void setPurpose(ConsentPurposeEntity purpose) {
        this.purpose = purpose;
    }

    public boolean isGranted() {
        return granted;
    }

    public void setGranted(boolean granted) {
        this.granted = granted;
    }

    public PolicyVersionEntity getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(PolicyVersionEntity policyVersion) {
        this.policyVersion = policyVersion;
    }

    public RegionCode getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(RegionCode regionCode) {
        this.regionCode = regionCode;
    }

    public ConsentSource getSource() {
        return source;
    }

    public void setSource(ConsentSource source) {
        this.source = source;
    }

    public UUID getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(UUID experimentId) {
        this.experimentId = experimentId;
    }

    public String getVariantKey() {
        return variantKey;
    }

    public void setVariantKey(String variantKey) {
        this.variantKey = variantKey;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
