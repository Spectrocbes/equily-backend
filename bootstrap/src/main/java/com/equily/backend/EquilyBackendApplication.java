package com.equily.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.equily")
public class EquilyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EquilyBackendApplication.class, args);
    }

}
