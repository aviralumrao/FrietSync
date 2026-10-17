package com.frietsync.backend.config.integration;

import com.frietsync.backend.service.integration.OAuthExchangeException;
import com.frietsync.backend.service.integration.ReauthorizationRequiredException;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleResponseErrorHandler extends DefaultResponseErrorHandler {
    private static final Pattern ERROR_CODE = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        Matcher matcher = ERROR_CODE.matcher(body);
        String errorCode = matcher.find() ? matcher.group(1) : "";

        if (url.getPath().endsWith("/revoke") && (status == 400 || status == 401)) return;
        if ("invalid_grant".equals(errorCode)) throw new ReauthorizationRequiredException();
        throw new OAuthExchangeException();
    }
}
