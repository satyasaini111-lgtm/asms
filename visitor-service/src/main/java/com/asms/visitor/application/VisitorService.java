package com.asms.visitor.application;

import com.asms.visitor.api.dto.VisitorEntryRequest;
import com.asms.visitor.domain.VisitorRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VisitorService {
    VisitorRequest requestEntry(VisitorEntryRequest request, String securityUserId);
    VisitorRequest approve(String id, String residentUserId);
    VisitorRequest reject(String id, String residentUserId);
    VisitorRequest checkIn(String id, String securityUserId);
    VisitorRequest checkOut(String id, String securityUserId);
    Page<VisitorRequest> listBySociety(String societyId, Pageable pageable);
    void expirePendingRequests();
}
