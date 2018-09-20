package io.axoniq.axonserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Author: marc
 */
@SpringBootApplication(scanBasePackages = "io.axoniq")
@EnableAsync
@EnableScheduling
@EnableJpaRepositories("io.axoniq")
@EntityScan("io.axoniq")
public class AxonServer {
    public static void main(String[] args) {
        System.setProperty("spring.config.name", "axonserver");
        SpringApplication.run(AxonServer.class, args);
    }


}