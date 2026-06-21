package com.asms.notification.application;

// Abstract Factory Pattern — each channel sends a different way
public interface NotificationChannel {
    void send(String recipient, String subject, String body);
    String channelType();
}
