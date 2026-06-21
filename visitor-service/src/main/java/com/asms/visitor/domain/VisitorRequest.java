package com.asms.visitor.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "visitor_requests")
public class VisitorRequest {

    @Id
    private String id;

    @Indexed
    private String societyId;

    @Indexed
    private String residentUserId;

    private String visitorName;
    private String visitorPhone;
    private String vehicleNumber;
    private String purpose;
    private VisitorStatus status;

    @Indexed(expireAfterSeconds = 300) // 5-minute TTL for PENDING requests
    private Instant expiresAt;

    @CreatedDate
    private Instant requestedAt;

    private Instant actionAt;
    private String actionByUserId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSocietyId() { return societyId; }
    public void setSocietyId(String societyId) { this.societyId = societyId; }
    public String getResidentUserId() { return residentUserId; }
    public void setResidentUserId(String residentUserId) { this.residentUserId = residentUserId; }
    public String getVisitorName() { return visitorName; }
    public void setVisitorName(String visitorName) { this.visitorName = visitorName; }
    public String getVisitorPhone() { return visitorPhone; }
    public void setVisitorPhone(String visitorPhone) { this.visitorPhone = visitorPhone; }
    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public VisitorStatus getStatus() { return status; }
    public void setStatus(VisitorStatus status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getActionAt() { return actionAt; }
    public void setActionAt(Instant actionAt) { this.actionAt = actionAt; }
    public String getActionByUserId() { return actionByUserId; }
    public void setActionByUserId(String actionByUserId) { this.actionByUserId = actionByUserId; }
}
