package com.saga.sattolux.core.auth;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RsaUtil {

    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;
    // SHA-256 hash + SHA-256 MGF1 — 브라우저 Web Crypto API 와 동일한 파라미터
    private static final OAEPParameterSpec OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT
    );

    private final Map<String, KeyPair> keyStore = new ConcurrentHashMap<>();

    public Map<String, String> generateKeyPair(String sessionId) throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        generator.initialize(KEY_SIZE, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        keyStore.put(sessionId, keyPair);

        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return Map.of("sessionId", sessionId, "publicKey", publicKey);
    }

    public String decrypt(String sessionId, String encryptedBase64) throws Exception {
        KeyPair keyPair = keyStore.remove(sessionId);
        if (keyPair == null) throw new IllegalArgumentException("Invalid or expired session");

        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), OAEP_SPEC);
        return new String(cipher.doFinal(encrypted));
    }
}
