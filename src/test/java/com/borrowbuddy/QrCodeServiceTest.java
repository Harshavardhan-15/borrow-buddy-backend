package com.borrowbuddy;

import com.borrowbuddy.service.QrCodeService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    void generatesUniqueTokensAndVerifiesOnlyTheHash() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = qrCodeService.generateRawToken();
            assertTrue(tokens.add(token), "Tokens must be unique");
            assertFalse(token.contains("="));
        }

        String token = qrCodeService.generateRawToken();
        String hash = qrCodeService.hash(token);

        assertEquals(64, hash.length(), "SHA-256 hex hash must be 64 characters");
        assertNotEquals(token, hash);
        assertTrue(qrCodeService.matches(token, hash));
        assertFalse(qrCodeService.matches(token + "x", hash));
        assertFalse(qrCodeService.matches(token, null));
    }

    @Test
    void producesScannablePngDataUrl() {
        String dataUrl = qrCodeService.toPngDataUrl(qrCodeService.generateRawToken());
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        assertTrue(dataUrl.length() > 500);
    }
}
