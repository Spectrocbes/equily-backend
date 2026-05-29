package com.equily.backend;

import com.equily.identity.infrastructure.security.JwtService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestJwtConfig {

  @Bean
  @Primary
  public JwtService jwtService() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    return new JwtService(keyPair.getPrivate(), keyPair.getPublic());
  }
}
