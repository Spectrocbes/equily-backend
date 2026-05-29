package com.equily.identity.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;

// Minimal bootstrap so @DataJpaTest can locate JPA entities and repositories
// in com.equily.identity.infrastructure.* when the bootstrap module is not on the test classpath.
@SpringBootApplication
class IdentityInfrastructureTestApplication {}
