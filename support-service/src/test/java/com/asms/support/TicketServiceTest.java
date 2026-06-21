package com.asms.support;

import com.asms.support.api.dto.CreateTicketRequest;
import com.asms.support.api.dto.TicketResponse;
import com.asms.support.application.TicketServiceImpl;
import com.asms.support.domain.*;
import com.asms.support.exception.ResourceNotFoundException;
import com.asms.support.infrastructure.kafka.TicketEventPublisher;
import com.asms.support.infrastructure.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService Unit Tests")
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketEventPublisher eventPublisher;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private TicketStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new TicketStateMachine();
        ticketService = new TicketServiceImpl(ticketRepository, eventPublisher, stateMachine);
    }

    @Test
    @DisplayName("createTicket: success — persists OPEN ticket and publishes event")
    void createTicket_success() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Water leakage in bathroom", "Leaking pipe under sink",
                TicketCategory.PLUMBING, TicketPriority.P2_HIGH, "soc_001"
        );
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = ticketService.createTicket(request, "usr_123");

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.raisedByUserId()).isEqualTo("usr_123");
        assertThat(response.category()).isEqualTo(TicketCategory.PLUMBING);
        verify(eventPublisher, times(1)).publishTicketCreated(any(Ticket.class));
    }

    @Test
    @DisplayName("assignTicket: OPEN → IN_PROGRESS state transition")
    void assignTicket_openToInProgress() {
        Ticket ticket = buildTicket(TicketStatus.OPEN);
        when(ticketRepository.findById("tkt_001")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = ticketService.assignTicket("tkt_001", "staff_001", "admin_001");

        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.assignedToUserId()).isEqualTo("staff_001");
        verify(eventPublisher).publishStatusChanged(any(), eq("OPEN"));
    }

    @Test
    @DisplayName("resolveTicket: IN_PROGRESS → RESOLVED")
    void resolveTicket_inProgressToResolved() {
        Ticket ticket = buildTicket(TicketStatus.IN_PROGRESS);
        ticket.setAssignedToUserId("staff_001");
        when(ticketRepository.findById("tkt_001")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponse response = ticketService.resolveTicket("tkt_001", "Fixed the pipe", "staff_001");

        assertThat(response.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(response.resolutionNote()).isEqualTo("Fixed the pipe");
    }

    @Test
    @DisplayName("resolveTicket: OPEN → throws IllegalStateException")
    void resolveTicket_openTicket_throwsException() {
        Ticket ticket = buildTicket(TicketStatus.OPEN);
        when(ticketRepository.findById("tkt_001")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.resolveTicket("tkt_001", "note", "usr_001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Assign first");
    }

    @Test
    @DisplayName("getTicketById: unknown id — throws ResourceNotFoundException")
    void getTicketById_notFound() {
        when(ticketRepository.findById("tkt_unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getTicketById("tkt_unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Ticket buildTicket(TicketStatus status) {
        Ticket t = new Ticket();
        t.setId("tkt_001");
        t.setSocietyId("soc_001");
        t.setRaisedByUserId("usr_123");
        t.setTitle("Water leakage");
        t.setDescription("Pipe leaking");
        t.setCategory(TicketCategory.PLUMBING);
        t.setPriority(TicketPriority.P2_HIGH);
        t.setStatus(status);
        return t;
    }
}
