package com.equily.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.identity.domain.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtServicePemLoadingTest {

  @TempDir Path tempDir;

  @Test
  void production_constructor_loads_keys_from_filesystem() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();

    String privateB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
    Path privateKeyFile = tempDir.resolve("test-private.pem");
    Files.writeString(
        privateKeyFile,
        "-----BEGIN PRIVATE KEY-----\n" + privateB64 + "\n-----END PRIVATE KEY-----\n");

    String publicB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    Path publicKeyFile = tempDir.resolve("test-public.pem");
    Files.writeString(
        publicKeyFile, "-----BEGIN PUBLIC KEY-----\n" + publicB64 + "\n-----END PUBLIC KEY-----\n");

    JwtService service =
        new JwtService(
            privateKeyFile.toAbsolutePath().toString(), publicKeyFile.toAbsolutePath().toString());

    User user = User.register("pem@example.com", "hashedPassword", "PEM User");
    String token = service.generateAccessToken(user);
    assertThat(token).isNotBlank();
    assertThat(service.isTokenValid(token)).isTrue();
  }
}
