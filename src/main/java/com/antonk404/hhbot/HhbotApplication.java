package com.antonk404.hhbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HhbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(HhbotApplication.class, args);
	}

}
