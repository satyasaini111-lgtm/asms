package com.asms.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    @Override
    public void send(String recipient, String subject, String body) {
        // Mock: real implementation would use SendGrid/SES
        log.info("EMAIL → to={} subject={} body={}", recipient, subject, body);
    }

    @Override
    public String channelType() { return "EMAIL"; }
}
