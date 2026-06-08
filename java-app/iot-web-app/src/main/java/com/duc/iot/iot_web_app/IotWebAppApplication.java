package com.duc.iot.iot_web_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IotWebAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(IotWebAppApplication.class, args);
	}

}
