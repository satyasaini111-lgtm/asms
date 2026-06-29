package com.asms.amenity.application;

import com.asms.amenity.api.dto.AmenityResponse;
import com.asms.amenity.api.dto.BookingResponse;
import com.asms.amenity.api.dto.CreateAmenityRequest;
import com.asms.amenity.api.dto.CreateBookingRequest;
import com.asms.amenity.domain.Amenity;
import com.asms.amenity.domain.Booking;
import com.asms.amenity.domain.BookingStatus;
import com.asms.amenity.exception.ConflictException;
import com.asms.amenity.exception.ResourceNotFoundException;
import com.asms.amenity.infrastructure.AmenityRepository;
import com.asms.amenity.infrastructure.BookingRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Service
public class AmenityServiceImpl implements AmenityService {

    private final AmenityRepository amenityRepository;
    private final BookingRepository bookingRepository;

    public AmenityServiceImpl(AmenityRepository amenityRepository, BookingRepository bookingRepository) {
        this.amenityRepository = amenityRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public AmenityResponse create(CreateAmenityRequest req) {
        Amenity amenity = new Amenity();
        amenity.setSocietyId(req.societyId());
        amenity.setName(req.name());
        amenity.setType(req.type());
        amenity.setCapacity(req.capacity());
        amenity.setHourlyRate(req.hourlyRate());
        amenity.setDescription(req.description());
        amenity.setOperatingHours(req.operatingHours());
        return AmenityResponse.from(amenityRepository.save(amenity));
    }

    @Override
    @Cacheable(value = "amenities", key = "#id")
    public AmenityResponse getById(String id) {
        return AmenityResponse.from(amenityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity", id)));
    }

    @Override
    public Page<AmenityResponse> listBySociety(String societyId, Pageable pageable) {
        return amenityRepository.findBySocietyIdAndActiveTrue(societyId, pageable)
                .map(AmenityResponse::from);
    }

    @Override
    @CacheEvict(value = "amenities", key = "#id")
    public AmenityResponse update(String id, CreateAmenityRequest req) {
        Amenity amenity = amenityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity", id));
        amenity.setName(req.name());
        amenity.setType(req.type());
        amenity.setCapacity(req.capacity());
        amenity.setHourlyRate(req.hourlyRate());
        amenity.setDescription(req.description());
        amenity.setOperatingHours(req.operatingHours());
        return AmenityResponse.from(amenityRepository.save(amenity));
    }

    @Override
    @CacheEvict(value = "amenities", key = "#id")
    public void deactivate(String id) {
        Amenity amenity = amenityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity", id));
        amenity.setActive(false);
        amenityRepository.save(amenity);
    }

    @Override
    public BookingResponse createBooking(CreateBookingRequest req) {
        Amenity amenity = amenityRepository.findById(req.amenityId())
                .orElseThrow(() -> new ResourceNotFoundException("Amenity", req.amenityId()));

        List<Booking> conflicts = bookingRepository.findConflictingBookings(
                req.amenityId(), req.slotStart(), req.slotEnd());
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Amenity slot is already booked for the requested time window");
        }

        long hours = Math.max(Duration.between(req.slotStart(), req.slotEnd()).toHours(), 1);
        BigDecimal rate = amenity.getHourlyRate() != null ? amenity.getHourlyRate() : BigDecimal.ZERO;

        Booking booking = new Booking();
        booking.setAmenityId(req.amenityId());
        booking.setUserId(req.userId());
        booking.setSocietyId(req.societyId());
        booking.setSlotStart(req.slotStart());
        booking.setSlotEnd(req.slotEnd());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(rate.multiply(BigDecimal.valueOf(hours)));

        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Override
    public BookingResponse getBookingById(String id) {
        return BookingResponse.from(bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id)));
    }

    @Override
    public Page<BookingResponse> listBookingsByUser(String userId, Pageable pageable) {
        return bookingRepository.findByUserId(userId, pageable).map(BookingResponse::from);
    }

    @Override
    public BookingResponse cancelBooking(String id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        booking.setStatus(BookingStatus.CANCELLED);
        return BookingResponse.from(bookingRepository.save(booking));
    }
}
