package com.rentamovil.gateway;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {

	public static void main(String[] args) {
		// Fechas en UTC, igual que el resto de servicios (modeling-conventions.md)
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(GatewayApplication.class, args);
	}

}
