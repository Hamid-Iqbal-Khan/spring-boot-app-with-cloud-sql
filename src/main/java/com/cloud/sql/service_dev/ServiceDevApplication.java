package com.cloud.sql.service_dev;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServiceDevApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceDevApplication.class, args);
        System.out.println("Hello");
	}

}
