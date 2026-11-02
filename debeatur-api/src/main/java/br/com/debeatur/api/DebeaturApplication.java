package br.com.debeatur.api;

import br.com.debeatur.indices.SgsClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.InstantSource;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DebeaturApplication {

    public static void main(String[] args) {
        SpringApplication.run(DebeaturApplication.class, args);
    }

    @Bean
    SgsClient sgsClient() {
        return new SgsClient();
    }

    @Bean
    InstantSource relogio() {
        return InstantSource.system();
    }
}
