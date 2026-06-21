package com.asms.visitor.application;

import com.asms.visitor.api.dto.VisitorEntryRequest;
import com.asms.visitor.domain.VisitorRequest;
import com.asms.visitor.domain.VisitorStatus;
import com.asms.visitor.exception.ResourceNotFoundException;
import com.asms.visitor.infrastructure.repository.VisitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VisitorServiceImpl implements VisitorService {

    private static final Logger log = LoggerFactory.getLogger(VisitorServiceImpl.class);

    private final VisitorRepository visitorRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public VisitorServiceImpl(VisitorRepository visitorRepository,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.visitorRepository = visitorRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public VisitorRequest requestEntry(VisitorEntryRequest request, String securityUserId) {
        VisitorRequest vr = new VisitorRequest();
        vr.setId("vis_" + UUID.randomUUID().toString().replace("-", ""));
        vr.setSocietyId(request.societyId());
        vr.setResidentUserId(request.residentUserId());
        vr.setVisitorName(request.visitorName());
        vr.setVisitorPhone(request.visitorPhone());
        vr.setVehicleNumber(request.vehicleNumber());
        vr.setPurpose(request.purpose());
        vr.setStatus(VisitorStatus.PENDING);
        vr.setExpiresAt(Instant.now().plusSeconds(300)); // 5-minute TTL

        VisitorRequest saved = visitorRepository.save(vr);

        publishEvent("visitor.entry.requested", saved.getId(), Map.of(
                "eventType", "VISITOR_ENTRY_REQUESTED",
                "visitorRequestId", saved.getId(),
                "residentUserId", saved.getResidentUserId(),
                "visitorName", saved.getVisitorName(),
                "societyId", saved.getSocietyId()
        ));
        return saved;
    }

    @Override
    public VisitorRequest approve(String id, String residentUserId) {
        VisitorRequest vr = findOrThrow(id);
        if (vr.getStatus() != VisitorStatus.PENDING) {
            throw new IllegalStateException("Can only approve PENDING requests");
        }
        vr.setStatus(VisitorStatus.APPROVED);
        vr.setActionAt(Instant.now());
        vr.setActionByUserId(residentUserId);
        VisitorRequest saved = visitorRepository.save(vr);
        publishEvent("visitor.approved", id, Map.of("eventType", "VISITOR_APPROVED", "visitorRequestId", id));
        return saved;
    }

    @Override
    public VisitorRequest reject(String id, String residentUserId) {
        VisitorRequest vr = findOrThrow(id);
        vr.setStatus(VisitorStatus.REJECTED);
        vr.setActionAt(Instant.now());
        vr.setActionByUserId(residentUserId);
        VisitorRequest saved = visitorRepository.save(vr);
        publishEvent("visitor.rejected", id, Map.of("eventType", "VISITOR_REJECTED", "visitorRequestId", id));
        return saved;
    }

    @Override
    public VisitorRequest checkIn(String id, String securityUserId) {
        VisitorRequest vr = findOrThrow(id);
        if (vr.getStatus() != VisitorStatus.APPROVED) {
            throw new IllegalStateException("Visitor must be APPROVED before check-in");
        }
        vr.setStatus(VisitorStatus.CHECKED_IN);
        return visitorRepository.save(vr);
    }

    @Override
    public VisitorRequest checkOut(String id, String securityUserId) {
        VisitorRequest vr = findOrThrow(id);
        if (vr.getStatus() != VisitorStatus.CHECKED_IN) {
            throw new IllegalStateException("Visitor is not currently checked in");
        }
        vr.setStatus(VisitorStatus.CHECKED_OUT);
        return visitorRepository.save(vr);
    }

    @Override
    public Page<VisitorRequest> listBySociety(String societyId, Pageable pageable) {
        return visitorRepository.findBySocietyId(societyId, pageable);
    }

    @Override
    @Scheduled(fixedDelay = 60000)
    public void expirePendingRequests() {
        List<VisitorRequest> expired =
                visitorRepository.findByStatusAndExpiresAtBefore(VisitorStatus.PENDING, Instant.now());
        for (VisitorRequest vr : expired) {
            vr.setStatus(VisitorStatus.EXPIRED);
            visitorRepository.save(vr);
            log.info("Visitor request expired: {}", vr.getId());
        }
    }

    private VisitorRequest findOrThrow(String id) {
        return visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor request not found: " + id));
    }

    private void publishEvent(String topic, String key, Map<String, Object> event) {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Event publish failed topic={}: {}", topic, ex.getMessage());
                });
    }
}
