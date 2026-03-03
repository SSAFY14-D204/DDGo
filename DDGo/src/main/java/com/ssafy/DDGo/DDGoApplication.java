package com.ssafy.DDGo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class DDGoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DDGoApplication.class, args);
    }
}
