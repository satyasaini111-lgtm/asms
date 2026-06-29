package com.asms.amenity.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "amenities")
public class Amenity {

    @Id
    private String id;

    @Indexed
    private String societyId;

    private String name;
    private AmenityType type;
    private int capacity;
    private BigDecimal hourlyRate;
    private boolean active = true;
    private String description;
    private String operatingHours;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSocietyId() { return societyId; }
    public void setSocietyId(String societyId) { this.societyId = societyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AmenityType getType() { return type; }
    public void setType(AmenityType type) { this.type = type; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }
}
