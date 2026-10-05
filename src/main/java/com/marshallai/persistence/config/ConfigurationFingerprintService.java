package com.marshallai.persistence.config;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConfigurationFingerprintService {

    public String fingerprint(Map<String, ?> configuration) {

        if (configuration == null || configuration.isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuration cannot be null or empty"
            );
        }

        String canonicalConfiguration =
                configuration.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry.comparingByKey(
                                        Comparator.naturalOrder()
                                )
                        )
                        .map(
                                entry ->
                                        entry.getKey()
                                                + "="
                                                + String.valueOf(
                                                entry.getValue()
                                        )
                        )
                        .collect(
                                Collectors.joining("|")
                        );

        return sha256(canonicalConfiguration);
    }

    private String sha256(String value) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder hexadecimal =
                    new StringBuilder();

            for (byte currentByte : hash) {
                hexadecimal.append(
                        String.format(
                                "%02x",
                                currentByte
                        )
                );
            }

            return hexadecimal.toString();

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }
}
