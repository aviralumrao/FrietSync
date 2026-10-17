package com.frietsync.backend.service.integration;

public class OAuthExchangeException extends RuntimeException {
    public OAuthExchangeException() {
        super("Google authorization service request failed");
    }
}
