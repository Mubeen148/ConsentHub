package io.consenthub.domain.entity;

import io.consenthub.domain.enums.ConsentSource;
import io.consenthub.domain.enums.RegionCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Strictly append-only immutable consent proof ledger row.
 * Protected at database level by PostgreSQL trigger trg_consent_history_append_only.
 */
@Entity
@Immutable
@Table(name = "consent_history")
public class ConsentHistoryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "subject_key", nullable = false, updatable = false, length = 128)
    private String subjectKey;

    @Column(name = "purpose_slug", nullable = false, updatable = false, length = 64)
    private String purposeSlug;

    @Column(name = "purpose_name", nullable = false, updatable = false, length = 120)
    private String purposeName;

    @Column(name = "previous_granted", updatable = false)
    private Boolean previousGranted;

    @Column(name = "new_granted", nullable = false, updatable = false)
    private boolean newGranted;

    @Column(name = "policy_version_label", nullable = false, updatable = false, length = 32)
    private String policyVersionLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", nullable = false, updatable = false, length = 20)
    private RegionCode regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 32)
    private ConsentSource source;

    @Column(name = "ip_hash", updatable = false, length = 64)
    private String ipHash;

    @Column(name = "user_agent_summary", updatable = false, length = 120)
    private String userAgentSummary;

    @Column(name = "experiment_variant", updatable = false, length = 64)
    private String experimentVariant;

    @Column(name = "proof_hash", nullable = false, updatable = false, length = 64)
    private String proofHash;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.occurredAt == null) {
            this.occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
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

    public String getPurposeSlug() {
        return purposeSlug;
    }

    public void setPurposeSlug(String purposeSlug) {
        this.purposeSlug = purposeSlug;
    }

    public String getPurposeName() {
        return purposeName;
    }

    public void setPurposeName(String purposeName) {
        this.purposeName = purposeName;
    }

    public Boolean getPreviousGranted() {
        return previousGranted;
    }

    public void setPreviousGranted(Boolean previousGranted) {
        this.previousGranted = previousGranted;
    }

    public boolean isNewGranted() {
        return newGranted;
    }

    public void setNewGranted(boolean newGranted) {
        this.newGranted = newGranted;
    }

    public String getPolicyVersionLabel() {
        return policyVersionLabel;
    }

    public void setPolicyVersionLabel(String policyVersionLabel) {
        this.policyVersionLabel = policyVersionLabel;
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

    public String getIpHash() {
        return ipHash;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    public String getUserAgentSummary() {
        return userAgentSummary;
    }

    public void setUserAgentSummary(String userAgentSummary) {
        this.userAgentSummary = userAgentSummary;
    }

    public String getExperimentVariant() {
        return experimentVariant;
    }

    public void setExperimentVariant(String experimentVariant) {
        this.experimentVariant = experimentVariant;
    }

    public String getProofHash() {
        return proofHash;
    }

    public void setProofHash(String proofHash) {
        this.proofHash = proofHash;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
