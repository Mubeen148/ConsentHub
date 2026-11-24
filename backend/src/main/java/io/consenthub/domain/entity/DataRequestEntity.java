package io.consenthub.domain.entity;

import io.consenthub.domain.enums.DataRequestStatus;
import io.consenthub.domain.enums.DataRequestType;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_requests")
public class DataRequestEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, updatable = false, length = 20)
    private DataRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private DataRequestStatus status = DataRequestStatus.PENDING;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "processed_by")
    private UserEntity processedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "export_payload_json")
    private Map<String, Object> exportPayloadJson;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.requestedAt == null) {
            this.requestedAt = now;
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

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public DataRequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(DataRequestType requestType) {
        this.requestType = requestType;
    }

    public DataRequestStatus getStatus() {
        return status;
    }

    public void setStatus(DataRequestStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public UserEntity getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(UserEntity processedBy) {
        this.processedBy = processedBy;
    }

    public Map<String, Object> getExportPayloadJson() {
        return exportPayloadJson;
    }

    public void setExportPayloadJson(Map<String, Object> exportPayloadJson) {
        this.exportPayloadJson = exportPayloadJson;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
