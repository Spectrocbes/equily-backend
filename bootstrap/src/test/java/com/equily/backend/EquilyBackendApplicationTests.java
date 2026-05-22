package com.equily.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EquilyBackendApplicationTests {

    @Disabled("Requires Docker and a configured datasource — re-enable when Docker Compose and application.yml are set up")
    @Test
    void contextLoads() {
    }

}
