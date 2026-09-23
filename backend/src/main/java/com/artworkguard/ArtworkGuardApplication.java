package com.artworkguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class ArtworkGuardApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArtworkGuardApplication.class, args);
	}

}
