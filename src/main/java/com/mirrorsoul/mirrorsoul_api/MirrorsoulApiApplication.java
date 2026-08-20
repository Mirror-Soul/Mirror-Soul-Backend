package com.mirrorsoul.mirrorsoul_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MirrorsoulApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MirrorsoulApiApplication.class, args);
	}

}
