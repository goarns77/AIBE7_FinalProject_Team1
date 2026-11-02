package org.example.springtestci.document.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DocumentUrlSigner {
  private final byte[] key;

  public DocumentUrlSigner(@Value("${app.storage.signing-secret}") String secret) {
    this.key = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String sign(long documentId, long userId, int version, long expires) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal(
                  ("document\n" + documentId + "\n" + userId + "\n" + version + "\n" + expires)
                      .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("Cannot sign document URL", exception);
    }
  }

  public boolean valid(long documentId, long userId, int version, long expires, String signature) {
    return signature != null
        && MessageDigest.isEqual(
            sign(documentId, userId, version, expires).getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8));
  }
}
