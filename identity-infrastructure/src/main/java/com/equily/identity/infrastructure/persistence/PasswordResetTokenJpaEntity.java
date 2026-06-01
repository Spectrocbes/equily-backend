package com.equily.identity.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_tokens", schema = "identity")
class PasswordResetTokenJpaEntity extends AbstractTokenJpaEntity {

  protected PasswordResetTokenJpaEntity() {}
}
