package com.echoe.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EchoeBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(EchoeBackendApplication.class, args);
	}

}
