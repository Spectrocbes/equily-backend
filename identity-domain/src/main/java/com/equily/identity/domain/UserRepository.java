package com.equily.identity.domain;

import java.util.Optional;

public interface UserRepository {
  void save(User user);

  Optional<User> findByEmail(String email);

  Optional<User> findById(UserId id);

  boolean existsByEmail(String email);
}
