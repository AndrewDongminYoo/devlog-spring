package com.sparta.devlogspring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class DevlogSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevlogSpringApplication.class, args);
    }

}
