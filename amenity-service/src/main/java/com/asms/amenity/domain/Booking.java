package com.asms.amenity.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "bookings")
public class Booking {

    @Id
    private String id;

    @Indexed
    private String amenityId;

    @Indexed
    private String userId;

    private String societyId;
    private Instant slotStart;
    private Instant slotEnd;
    private BookingStatus status;
    private BigDecimal totalAmount;
    private String paymentId;

    @CreatedDate
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAmenityId() { return amenityId; }
    public void setAmenityId(String amenityId) { this.amenityId = amenityId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSocietyId() { return societyId; }
    public void setSocietyId(String societyId) { this.societyId = societyId; }
    public Instant getSlotStart() { return slotStart; }
    public void setSlotStart(Instant slotStart) { this.slotStart = slotStart; }
    public Instant getSlotEnd() { return slotEnd; }
    public void setSlotEnd(Instant slotEnd) { this.slotEnd = slotEnd; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public Instant getCreatedAt() { return createdAt; }
}
