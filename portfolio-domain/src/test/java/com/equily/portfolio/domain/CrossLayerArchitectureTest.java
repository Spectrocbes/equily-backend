package com.equily.portfolio.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.equily.identity.domain.UserId;
import com.equily.portfolio.domain.account.AccountSubType;
import com.equily.shared.Money;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

@AnalyzeClasses(packages = "com.equily", importOptions = ImportOption.DoNotIncludeTests.class)
class CrossLayerArchitectureTest {

  @ArchTest
  static final ArchRule reconstruct_must_only_be_called_from_infrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "com.equily.portfolio.infrastructure..",
              "com.equily.portfolio.domain..") // domain tests can call it too
          .should()
          .callMethod(
              FinancialAccount.class,
              "reconstruct",
              FinancialAccountId.class,
              String.class,
              AccountType.class,
              Money.class,
              List.class,
              String.class,
              UserId.class,
              AccountSubType.class)
          .as("reconstruct() must only be called from portfolio-infrastructure or domain tests");
}
