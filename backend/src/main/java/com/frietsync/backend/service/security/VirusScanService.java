package com.frietsync.backend.service.security;

import fi.solita.clamav.ClamAVClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
public class VirusScanService {

    @Value("${clamav.host:localhost}")
    private String clamavHost;

    @Value("${clamav.port:3310}")
    private int clamavPort;

    @Value("${clamav.enabled:true}")
    private boolean enabled;

    public void scanFile(MultipartFile file) {
        if (!enabled) {
            return;
        }
        if (file == null || file.isEmpty()) {
            return;
        }

        try {
            ClamAVClient client = new ClamAVClient(clamavHost, clamavPort);
            byte[] scanResult = client.scan(file.getInputStream());

            if (!ClamAVClient.isCleanReply(scanResult)) {
                String msg = new String(scanResult);
                log.warn("Malware detected in uploaded file {}: {}", file.getOriginalFilename(), msg);
                throw new SecurityException("File upload rejected: Malware or virus detected");
            }
        } catch (IOException e) {
            log.error("Failed to connect to ClamAV virus scanner at {}:{}", clamavHost, clamavPort, e);
            throw new IllegalStateException("Virus scanner is currently unavailable. Upload failed.");
        }
    }
}
