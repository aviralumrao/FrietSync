package com.frietsync.backend.service.recaptcha;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.frietsync.backend.exception.BadRequestException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class RecaptchaService {

    @Value("${recaptcha.secret-key:}")
    private String secretKey;

    @Value("${recaptcha.min-score:0.5}")
    private double minScore;

    @Value("${recaptcha.allowed-domain:}")
    private String allowedDomain;

    @Value("${recaptcha.enabled:false}")
    private boolean enabled;

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private final RestTemplate restTemplate = new RestTemplate();

    public void verify(String token, String expectedAction) {
        if (!enabled) {
            return;
        }

        if (secretKey == null || secretKey.isBlank()) {
            log.warn("reCAPTCHA is enabled but RECAPTCHA_SECRET_KEY is not configured.");
            return;
        }

        if (token == null || token.isBlank()) {
            throw new BadRequestException("reCAPTCHA verification token is missing");
        }

        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add("secret", secretKey);
        requestMap.add("response", token);

        RecaptchaVerifyResponse response;
        try {
            response = restTemplate.postForObject(VERIFY_URL, requestMap, RecaptchaVerifyResponse.class);
        } catch (Exception ex) {
            log.error("Failed to connect to Google reCAPTCHA verification service", ex);
            throw new BadRequestException("Failed to verify CAPTCHA. Please try again later.");
        }

        if (response == null || !response.isSuccess()) {
            throw new BadRequestException("reCAPTCHA verification failed");
        }

        if (response.getScore() < minScore) {
            log.warn("reCAPTCHA score {} is below minimum threshold {}", response.getScore(), minScore);
            throw new BadRequestException("reCAPTCHA verification failed due to low score");
        }

        if (expectedAction != null && !expectedAction.equalsIgnoreCase(response.getAction())) {
            log.warn("reCAPTCHA action mismatch: expected {}, got {}", expectedAction, response.getAction());
            throw new BadRequestException("reCAPTCHA action mismatch");
        }

        if (allowedDomain != null && !allowedDomain.isBlank() && !allowedDomain.equalsIgnoreCase(response.getHostname())) {
            log.warn("reCAPTCHA hostname mismatch: expected {}, got {}", allowedDomain, response.getHostname());
            throw new BadRequestException("reCAPTCHA hostname mismatch");
        }
    }

    @Data
    public static class RecaptchaVerifyResponse {
        private boolean success;
        private double score;
        private String action;
        private String hostname;
        @JsonProperty("error-codes")
        private String[] errorCodes;
    }
}
