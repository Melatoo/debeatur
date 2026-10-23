package br.com.debeatur.api;

import br.com.debeatur.indices.SgsClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DebeaturApplication {

    public static void main(String[] args) {
        SpringApplication.run(DebeaturApplication.class, args);
    }

    @Bean
    SgsClient sgsClient() {
        return new SgsClient();
    }
}
