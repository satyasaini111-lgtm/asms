package com.asms.amenity.infrastructure;

import com.asms.amenity.domain.Booking;
import com.asms.amenity.domain.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends MongoRepository<Booking, String> {
    Page<Booking> findByUserId(String userId, Pageable pageable);

    @Query("{ 'amenityId': ?0, 'status': 'CONFIRMED', $or: [ { 'slotStart': { $lt: ?2 }, 'slotEnd': { $gt: ?1 } } ] }")
    List<Booking> findConflictingBookings(String amenityId, Instant newStart, Instant newEnd);
}
