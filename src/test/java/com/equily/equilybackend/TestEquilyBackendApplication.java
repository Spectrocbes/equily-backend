package com.equily.equilybackend;

import org.springframework.boot.SpringApplication;

public class TestEquilyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(EquilyBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
