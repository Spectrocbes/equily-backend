package com.equily.portfolio.infrastructure.persistence;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.FinancialAccount;
import com.equily.portfolio.domain.FinancialAccountId;
import com.equily.portfolio.domain.FinancialAccountRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class FinancialAccountRepositoryAdapter implements FinancialAccountRepository {

  private final FinancialAccountJpaRepository jpaRepository;

  FinancialAccountRepositoryAdapter(FinancialAccountJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(FinancialAccount account) {
    Optional<FinancialAccountJpaEntity> existing = jpaRepository.findById(account.id().value());
    if (existing.isPresent()) {
      FinancialAccountJpaEntity entity = existing.get();
      FinancialAccountMapper.updateJpaEntity(entity, account);
      jpaRepository.save(entity);
    } else {
      jpaRepository.save(FinancialAccountMapper.toJpa(account));
    }
  }

  @Override
  public Optional<FinancialAccount> findById(FinancialAccountId id) {
    return jpaRepository.findById(id.value()).map(FinancialAccountMapper::toDomain);
  }

  @Override
  public List<FinancialAccount> findAllByOwnerId(UserId ownerId) {
    return jpaRepository.findAllByUserId(ownerId.value()).stream()
        .map(FinancialAccountMapper::toDomain)
        .toList();
  }

  @Override
  public List<FinancialAccount> findAll() {
    return jpaRepository.findAll().stream().map(FinancialAccountMapper::toDomain).toList();
  }
}
