package com.asms.workflow.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class RwaApprovalHandler extends ApprovalHandler {

    private static final Logger log = LoggerFactory.getLogger(RwaApprovalHandler.class);
    private static final BigDecimal RWA_APPROVAL_THRESHOLD = new BigDecimal("50000");

    @Override
    public boolean handle(Map<String, Object> request) {
        BigDecimal contractValue = new BigDecimal(
                String.valueOf(request.getOrDefault("contractValue", "0")));
        log.info("RWA approval check: contractValue={}", contractValue);

        if (contractValue.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("RWA check FAILED: invalid contract value");
            return false;
        }

        if (contractValue.compareTo(RWA_APPROVAL_THRESHOLD) > 0) {
            log.info("RWA check: contract value > {} — escalating to Admin", RWA_APPROVAL_THRESHOLD);
        } else {
            log.info("RWA check PASSED — within RWA approval limit");
        }
        return passToNext(request);
    }
}
