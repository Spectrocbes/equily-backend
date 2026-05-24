package com.equily.portfolio.infrastructure.persistence;

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
    FinancialAccountJpaEntity entity = FinancialAccountMapper.toJpa(account);
    // existsById check lets Persistable.isNew() signal persist() vs merge() correctly.
    // Without it, toJpa() always creates isNew=true and re-saving an existing account
    // would attempt a duplicate INSERT.
    if (jpaRepository.existsById(account.id().value())) {
      entity.markAsExisting();
    }
    jpaRepository.save(entity);
  }

  @Override
  public Optional<FinancialAccount> findById(FinancialAccountId id) {
    return jpaRepository.findById(id.value()).map(FinancialAccountMapper::toDomain);
  }

  @Override
  public List<FinancialAccount> findAll() {
    return jpaRepository.findAll().stream().map(FinancialAccountMapper::toDomain).toList();
  }
}
