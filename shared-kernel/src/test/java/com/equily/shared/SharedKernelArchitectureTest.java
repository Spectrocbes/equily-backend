package com.equily.shared;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.equily.shared",
    importOptions = ImportOption.DoNotIncludeTests.class)
class SharedKernelArchitectureTest {

  @ArchTest
  static final ArchRule shared_kernel_must_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("com.equily.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..");

  @ArchTest
  static final ArchRule shared_kernel_must_not_depend_on_jpa =
      noClasses()
          .that()
          .resideInAPackage("com.equily.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("jakarta.persistence..");

  @ArchTest
  static final ArchRule shared_kernel_must_not_depend_on_lombok =
      noClasses()
          .that()
          .resideInAPackage("com.equily.shared..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("lombok..");
}
