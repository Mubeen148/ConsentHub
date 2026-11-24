package io.consenthub.domain.entity;

import io.consenthub.domain.enums.ExperimentStatus;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.ScreenName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "experiments")
public class ExperimentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_screen", nullable = false, length = 64)
    private ScreenName targetScreen;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_region", length = 20)
    private RegionCode targetRegion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExperimentStatus status = ExperimentStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variants_json", nullable = false)
    private List<ExperimentVariantConfig> variantsJson = new ArrayList<>();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ScreenName getTargetScreen() {
        return targetScreen;
    }

    public void setTargetScreen(ScreenName targetScreen) {
        this.targetScreen = targetScreen;
    }

    public RegionCode getTargetRegion() {
        return targetRegion;
    }

    public void setTargetRegion(RegionCode targetRegion) {
        this.targetRegion = targetRegion;
    }

    public ExperimentStatus getStatus() {
        return status;
    }

    public void setStatus(ExperimentStatus status) {
        this.status = status;
    }

    public List<ExperimentVariantConfig> getVariantsJson() {
        return variantsJson;
    }

    public void setVariantsJson(List<ExperimentVariantConfig> variantsJson) {
        this.variantsJson = variantsJson;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
