package io.consenthub.domain.entity;

import io.consenthub.domain.enums.BannerStyle;
import io.consenthub.domain.enums.ConsentModel;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.RegulatoryRegime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "region_rules")
public class RegionRuleEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", nullable = false, unique = true, length = 20)
    private RegionCode regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_name", nullable = false, length = 64)
    private RegulatoryRegime regimeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_model", nullable = false, length = 24)
    private ConsentModel consentModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "banner_style", nullable = false, length = 24)
    private BannerStyle bannerStyle;

    @Column(name = "show_reject_all", nullable = false)
    private boolean showRejectAll = true;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays = 365;

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
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RegionCode getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(RegionCode regionCode) {
        this.regionCode = regionCode;
    }

    public RegulatoryRegime getRegimeName() {
        return regimeName;
    }

    public void setRegimeName(RegulatoryRegime regimeName) {
        this.regimeName = regimeName;
    }

    public ConsentModel getConsentModel() {
        return consentModel;
    }

    public void setConsentModel(ConsentModel consentModel) {
        this.consentModel = consentModel;
    }

    public BannerStyle getBannerStyle() {
        return bannerStyle;
    }

    public void setBannerStyle(BannerStyle bannerStyle) {
        this.bannerStyle = bannerStyle;
    }

    public boolean isShowRejectAll() {
        return showRejectAll;
    }

    public void setShowRejectAll(boolean showRejectAll) {
        this.showRejectAll = showRejectAll;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
