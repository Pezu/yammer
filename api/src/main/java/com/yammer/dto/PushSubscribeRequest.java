package com.yammer.dto;

/** A browser PushSubscription sent to the server (shape matches {@code PushSubscription.toJSON()}). */
public record PushSubscribeRequest(String endpoint, Keys keys) {

    public record Keys(String p256dh, String auth) {
    }
}
