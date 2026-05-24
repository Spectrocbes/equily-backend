package com.equily.portfolio.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;

// Minimal bootstrap so @DataJpaTest can locate JPA entities and repositories
// in com.equily.portfolio.infrastructure.* when the bootstrap module is not on the test classpath.
@SpringBootApplication
class InfrastructureTestApplication {}
