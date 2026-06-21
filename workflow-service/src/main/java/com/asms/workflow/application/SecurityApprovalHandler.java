package com.asms.workflow.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SecurityApprovalHandler extends ApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityApprovalHandler.class);

    @Override
    public boolean handle(Map<String, Object> request) {
        String documentType = (String) request.getOrDefault("documentType", "");
        log.info("Security check: verifying document type '{}'", documentType);

        if (documentType.isBlank()) {
            log.warn("Security check FAILED: missing document type");
            return false;
        }
        log.info("Security check PASSED");
        return passToNext(request);
    }
}
