package com.equily.identity.infrastructure.persistence;

import com.equily.identity.domain.User;
import com.equily.identity.domain.UserId;
import com.equily.identity.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class UserRepositoryAdapter implements UserRepository {

  private final UserJpaRepository jpa;

  UserRepositoryAdapter(UserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void save(User user) {
    UserJpaEntity entity = toJpa(user);
    if (jpa.existsById(user.id().value())) {
      entity.markAsExisting();
    }
    jpa.save(entity);
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return jpa.findByEmail(email).map(this::toDomain);
  }

  @Override
  public Optional<User> findById(UserId id) {
    return jpa.findById(id.value()).map(this::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return jpa.existsByEmail(email);
  }

  private UserJpaEntity toJpa(User user) {
    return new UserJpaEntity(
        user.id().value(), user.email(), user.passwordHash(), user.displayName(), user.createdAt());
  }

  private User toDomain(UserJpaEntity entity) {
    return User.reconstruct(
        new UserId(entity.getId()),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getDisplayName(),
        entity.getCreatedAt());
  }
}
