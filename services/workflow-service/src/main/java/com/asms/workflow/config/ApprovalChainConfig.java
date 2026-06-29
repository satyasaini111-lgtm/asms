package com.asms.workflow.config;

import com.asms.workflow.application.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApprovalChainConfig {

    @Bean("vendorApprovalChain")
    public ApprovalHandler vendorApprovalChain(
            SecurityApprovalHandler security,
            RwaApprovalHandler rwa,
            AdminApprovalHandler admin) {
        security.setNext(rwa).setNext(admin);
        return security;
    }
}
