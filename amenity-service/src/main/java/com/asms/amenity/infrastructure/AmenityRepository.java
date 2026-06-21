package com.asms.amenity.infrastructure;

import com.asms.amenity.domain.Amenity;
import com.asms.amenity.domain.AmenityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AmenityRepository extends MongoRepository<Amenity, String> {
    Page<Amenity> findBySocietyIdAndActiveTrue(String societyId, Pageable pageable);
    List<Amenity> findBySocietyIdAndType(String societyId, AmenityType type);
}
