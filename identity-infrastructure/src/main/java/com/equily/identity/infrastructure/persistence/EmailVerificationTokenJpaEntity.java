package com.equily.identity.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_verification_tokens", schema = "identity")
class EmailVerificationTokenJpaEntity extends AbstractTokenJpaEntity {

  protected EmailVerificationTokenJpaEntity() {}
}
