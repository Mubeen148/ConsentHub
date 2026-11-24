package io.consenthub.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policy_versions")
public class PolicyVersionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version_number", nullable = false, unique = true)
    private int versionNumber;

    @Column(name = "version_label", nullable = false, unique = true, length = 32)
    private String versionLabel;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "content_markdown", nullable = false, columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(name = "requires_reconsent", nullable = false)
    private boolean requiresReconsent = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "published_by")
    private UserEntity publishedBy;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public void setContentMarkdown(String contentMarkdown) {
        this.contentMarkdown = contentMarkdown;
    }

    public boolean isRequiresReconsent() {
        return requiresReconsent;
    }

    public void setRequiresReconsent(boolean requiresReconsent) {
        this.requiresReconsent = requiresReconsent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UserEntity getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(UserEntity publishedBy) {
        this.publishedBy = publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
