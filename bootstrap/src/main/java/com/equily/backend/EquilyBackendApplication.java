package com.equily.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// @AutoConfigurationPackage registers only com.equily.backend (the declaring class's package).
// @EntityScan and @EnableJpaRepositories must explicitly extend coverage to all modules.
@SpringBootApplication(scanBasePackages = "com.equily")
@EntityScan("com.equily")
@EnableJpaRepositories("com.equily")
public class EquilyBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(EquilyBackendApplication.class, args);
  }
}
