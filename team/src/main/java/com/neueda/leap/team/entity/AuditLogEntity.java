package com.neueda.leap.team.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Audit_Logs")
public class AuditLogEntity {

    /**
     *
     * CREATE TABLE Audit_Logs (
     *     Audit_ID BIGSERIAL PRIMARY KEY,
     *     User_ID BIGINT NOT NULL,
     *     Affected_Table VARCHAR(100) NOT NULL,
     *     Record_ID BIGINT NOT NULL,
     *     -- record id used to indicate the primary key of the affected record in the affected table
     *     Action_Type VARCHAR(6) NOT NULL,
     *     Old_Value TEXT,
     *     New_Value TEXT,
     *     Timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
     *     FOREIGN KEY (User_ID) REFERENCES Users(User_ID),
     *     CONSTRAINT chk_audit_logs_action_type CHECK (Action_Type IN ('INSERT','UPDATE','DELETE'))
     * );
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Audit_ID")
    private Long auditLogId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "User_ID", nullable = false)
    private UserEntity userForAudit;

    @Column(name = "Affected_Table", nullable = false)
    private String affectedTable;

    @Column(name = "Record_ID", nullable = false)
    private Long recordId;

    @Column(name = "Action_Type", nullable = false)
    private String actionType;

    @Column(name = "Old_Value")
    private String oldValue;

    @Column(name = "New_Value")
    private String newValue;

    @Column(name = "Timestamp", nullable = false)
    private LocalDateTime timestamp;

    public AuditLogEntity(UserEntity userForAudit, String affectedTable, Long recordId, String actionType, String oldValue, String newValue) {
        this.userForAudit = userForAudit;
        this.affectedTable = affectedTable;
        this.recordId = recordId;
        this.actionType = actionType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = LocalDateTime.now();
    }

    public AuditLogEntity() {

    }

    public Long getAuditLogId() {
        return auditLogId;
    }

    public void setAuditLogId(Long auditLogId) {
        this.auditLogId = auditLogId;
    }

    public UserEntity getUserForAudit() {
        return userForAudit;
    }

    public void setUserForAudit(UserEntity userForAudit) {
        this.userForAudit = userForAudit;
    }

    public String getAffectedTable() {
        return affectedTable;
    }

    public void setAffectedTable(String affectedTable) {
        this.affectedTable = affectedTable;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
