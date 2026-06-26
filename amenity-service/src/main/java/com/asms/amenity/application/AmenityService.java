package com.asms.amenity.application;

import com.asms.amenity.api.dto.AmenityResponse;
import com.asms.amenity.api.dto.BookingResponse;
import com.asms.amenity.api.dto.CreateAmenityRequest;
import com.asms.amenity.api.dto.CreateBookingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AmenityService {
    AmenityResponse create(CreateAmenityRequest request);
    AmenityResponse getById(String id);
    Page<AmenityResponse> listBySociety(String societyId, Pageable pageable);
    AmenityResponse update(String id, CreateAmenityRequest request);
    void deactivate(String id);

    BookingResponse createBooking(CreateBookingRequest request);
    BookingResponse getBookingById(String id);
    Page<BookingResponse> listBookingsByUser(String userId, Pageable pageable);
    BookingResponse cancelBooking(String id);
}
