package com.asms.workflow.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AdminApprovalHandler extends ApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminApprovalHandler.class);

    @Override
    public boolean handle(Map<String, Object> request) {
        String vendorId = (String) request.getOrDefault("vendorId", "");
        log.info("Admin final approval for vendorId={}", vendorId);
        // Final approval gate — in production, triggers Keycloak VENDOR role assignment
        log.info("Admin approval GRANTED for vendorId={}", vendorId);
        return true;
    }
}
