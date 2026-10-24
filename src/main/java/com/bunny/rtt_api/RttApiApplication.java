package com.bunny.rtt_api;

import com.bunny.rtt_api.config.AppProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProps.class)
public class RttApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RttApiApplication.class, args);
	}

}
