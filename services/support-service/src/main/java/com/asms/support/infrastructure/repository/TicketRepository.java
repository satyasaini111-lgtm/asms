package com.asms.support.infrastructure.repository;

import com.asms.support.domain.Ticket;
import com.asms.support.domain.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends MongoRepository<Ticket, String> {
    Page<Ticket> findBySocietyId(String societyId, Pageable pageable);
    Page<Ticket> findBySocietyIdAndStatus(String societyId, TicketStatus status, Pageable pageable);
    Page<Ticket> findByRaisedByUserId(String userId, Pageable pageable);
}
