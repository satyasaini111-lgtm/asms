package com.asms.support.domain;

import java.time.Instant;

public record TicketHistory(
        String changedByUserId,
        TicketStatus fromStatus,
        TicketStatus toStatus,
        String note,
        Instant changedAt
) {}
