package com.dokpamine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DokpamineBeApplication {
    public static void main(String[] args) {
        SpringApplication.run(DokpamineBeApplication.class, args);
    }
}
