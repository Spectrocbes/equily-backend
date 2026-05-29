package com.equily.identity.infrastructure.usecase;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserRepository;
import com.equily.identity.domain.exception.InvalidCredentialsException;
import com.equily.identity.domain.exception.UserAlreadyExistsException;
import com.equily.identity.infrastructure.persistence.RefreshTokenService;
import com.equily.identity.infrastructure.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordEncoder passwordEncoder;

  public AuthService(
      UserRepository userRepository,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.passwordEncoder = passwordEncoder;
  }

  public AuthTokenPair register(String email, String password, String displayName) {
    if (userRepository.existsByEmail(email)) {
      throw new UserAlreadyExistsException(email);
    }
    String hash = passwordEncoder.encode(password);
    User user = User.register(email, hash, displayName);
    userRepository.save(user);
    return generateTokenPair(user);
  }

  public AuthTokenPair login(String email, String password) {
    User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(password, user.passwordHash())) {
      throw new InvalidCredentialsException();
    }
    return generateTokenPair(user);
  }

  public AuthTokenPair refresh(String rawRefreshToken) {
    String newRawToken = UUID.randomUUID() + UUID.randomUUID().toString();
    Optional<UserId> userIdOpt =
        refreshTokenService.rotateRefreshToken(rawRefreshToken, newRawToken);

    UserId userId = userIdOpt.orElseThrow(InvalidCredentialsException::new);

    User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);

    String newRefreshToken = refreshTokenService.createRefreshToken(userId);
    return new AuthTokenPair(jwtService.generateAccessToken(user), newRefreshToken);
  }

  public void logout(UserId userId) {
    refreshTokenService.revokeAllForUser(userId);
  }

  private AuthTokenPair generateTokenPair(User user) {
    String accessToken = jwtService.generateAccessToken(user);
    String refreshToken = refreshTokenService.createRefreshToken(user.id());
    return new AuthTokenPair(accessToken, refreshToken);
  }
}
