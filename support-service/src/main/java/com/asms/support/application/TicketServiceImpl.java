package com.asms.support.application;

import com.asms.support.api.dto.CreateTicketRequest;
import com.asms.support.api.dto.TicketResponse;
import com.asms.support.domain.*;
import com.asms.support.exception.ResourceNotFoundException;
import com.asms.support.infrastructure.kafka.TicketEventPublisher;
import com.asms.support.infrastructure.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);

    private final TicketRepository ticketRepository;
    private final TicketEventPublisher eventPublisher;
    private final TicketStateMachine stateMachine;

    public TicketServiceImpl(TicketRepository ticketRepository,
                              TicketEventPublisher eventPublisher,
                              TicketStateMachine stateMachine) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
        this.stateMachine = stateMachine;
    }

    @Override
    public TicketResponse createTicket(CreateTicketRequest request, String userId) {
        Ticket ticket = new Ticket();
        ticket.setId("tkt_" + UUID.randomUUID().toString().replace("-", ""));
        ticket.setSocietyId(request.societyId());
        ticket.setRaisedByUserId(userId);
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.addHistory(userId, null, TicketStatus.OPEN, "Ticket raised");

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket created: {}", saved.getId());
        eventPublisher.publishTicketCreated(saved);
        return TicketResponse.from(saved);
    }

    @Override
    public TicketResponse getTicketById(String id) {
        return ticketRepository.findById(id)
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }

    @Override
    public Page<TicketResponse> listTickets(String societyId, TicketStatus status,
                                             String userId, String role, Pageable pageable) {
        boolean isAdmin = "ADMIN".equals(role) || "RWA_MEMBER".equals(role) || "SUPPORT_STAFF".equals(role);

        if (isAdmin) {
            if (status != null) {
                return ticketRepository.findBySocietyIdAndStatus(societyId, status, pageable)
                        .map(TicketResponse::from);
            }
            return ticketRepository.findBySocietyId(societyId, pageable).map(TicketResponse::from);
        }
        // Owners/Tenants only see their own tickets
        return ticketRepository.findByRaisedByUserId(userId, pageable).map(TicketResponse::from);
    }

    @Override
    public TicketResponse assignTicket(String id, String assignedToUserId, String actorId) {
        Ticket ticket = findOrThrow(id);
        String prevStatus = ticket.getStatus().name();
        stateMachine.stateFor(ticket).assign(ticket, assignedToUserId);
        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publishStatusChanged(saved, prevStatus);
        return TicketResponse.from(saved);
    }

    @Override
    public TicketResponse resolveTicket(String id, String resolutionNote, String actorId) {
        Ticket ticket = findOrThrow(id);
        String prevStatus = ticket.getStatus().name();
        stateMachine.stateFor(ticket).resolve(ticket, resolutionNote);
        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publishStatusChanged(saved, prevStatus);
        return TicketResponse.from(saved);
    }

    @Override
    public TicketResponse closeTicket(String id, String actorId) {
        Ticket ticket = findOrThrow(id);
        String prevStatus = ticket.getStatus().name();
        stateMachine.stateFor(ticket).close(ticket);
        Ticket saved = ticketRepository.save(ticket);
        eventPublisher.publishStatusChanged(saved, prevStatus);
        return TicketResponse.from(saved);
    }

    private Ticket findOrThrow(String id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }
}
