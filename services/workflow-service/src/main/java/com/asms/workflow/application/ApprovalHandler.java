package com.asms.workflow.application;

import java.util.Map;

// Chain of Responsibility Pattern — approval steps chained for vendor onboarding
public abstract class ApprovalHandler {

    protected ApprovalHandler next;

    public ApprovalHandler setNext(ApprovalHandler next) {
        this.next = next;
        return next;
    }

    public abstract boolean handle(Map<String, Object> request);

    protected boolean passToNext(Map<String, Object> request) {
        if (next != null) return next.handle(request);
        return true;
    }
}
