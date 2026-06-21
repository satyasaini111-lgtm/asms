package com.asms.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SmsNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationChannel.class);

    @Override
    public void send(String recipient, String subject, String body) {
        // Mock: real implementation would use Twilio/MSG91
        log.info("SMS → to={} message={}", recipient, body);
    }

    @Override
    public String channelType() { return "SMS"; }
}
