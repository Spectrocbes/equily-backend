package com.equily.identity.infrastructure.security;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

  @Autowired
  public JwtService(
      @Value("${jwt.private-key-path}") String privateKeyPath,
      @Value("${jwt.public-key-path}") String publicKeyPath)
      throws Exception {
    this.privateKey = loadPrivateKey(privateKeyPath);
    this.publicKey = loadPublicKey(publicKeyPath);
  }

  public JwtService(PrivateKey privateKey, PublicKey publicKey) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.id().value().toString())
        .claim("email", user.email())
        .claim("displayName", user.displayName())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  public Claims parseToken(String token) {
    return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
  }

  public boolean isTokenValid(String token) {
    try {
      parseToken(token);
      return true;
    } catch (JwtException e) {
      return false;
    }
  }

  public UserId extractUserId(String token) {
    return UserId.of(parseToken(token).getSubject());
  }

  private PrivateKey loadPrivateKey(String path)
      throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    String pem = readPemContent(path);
    String cleaned =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
    byte[] decoded = Base64.getDecoder().decode(cleaned);
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
  }

  private PublicKey loadPublicKey(String path)
      throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    String pem = readPemContent(path);
    String cleaned =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
    byte[] decoded = Base64.getDecoder().decode(cleaned);
    return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
  }

  private String readPemContent(String path) throws IOException {
    InputStream classpathStream = getClass().getClassLoader().getResourceAsStream(path);
    if (classpathStream != null) {
      return new String(classpathStream.readAllBytes(), StandardCharsets.UTF_8);
    }
    return Files.readString(Path.of(path));
  }
}
