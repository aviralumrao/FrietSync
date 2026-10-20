package com.frietsync.backend.service.integration;

import com.frietsync.backend.config.integration.GitHubOAuthProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class GitHubTokenCipher {
    private static final int IV_SIZE = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public GitHubTokenCipher(GitHubOAuthProperties properties) {
        try {
            byte[] bytes = Base64.getDecoder().decode(properties.getEncryptionKey());
            if (bytes.length != 32) throw new IllegalArgumentException();
            key = new SecretKeySpec(bytes, "AES");
        } catch (RuntimeException ex) {
            throw new IllegalStateException("GITHUB_OAUTH_ENCRYPTION_KEY must be base64 for 32 random bytes", ex);
        }
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_SIZE];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt GitHub credential", ex);
        }
    }

    public String decrypt(String value) {
        try {
            byte[] packed = Base64.getDecoder().decode(value);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, packed, 0, IV_SIZE));
            return new String(cipher.doFinal(packed, IV_SIZE, packed.length - IV_SIZE), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt GitHub credential", ex);
        }
    }
}
