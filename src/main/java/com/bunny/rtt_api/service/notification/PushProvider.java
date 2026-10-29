package com.bunny.rtt_api.service.notification;

public interface PushProvider {
    void sendPush(String target, String title, String body);
    String name();
}
