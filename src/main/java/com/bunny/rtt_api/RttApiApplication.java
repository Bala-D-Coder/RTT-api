package com.bunny.rtt_api;

import com.bunny.rtt_api.config.AppProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProps.class)
public class RttApiApplication {

	public static void main(String[] args) {
		loadDotenv();
		SpringApplication.run(RttApiApplication.class, args);
	}

	private static void loadDotenv() {
		try {
			java.nio.file.Path envFile = java.nio.file.Path.of(".env");
			if (!java.nio.file.Files.exists(envFile)) {
				envFile = java.nio.file.Path.of("../.env");
			}
			if (java.nio.file.Files.exists(envFile)) {
				for (String line : java.nio.file.Files.readAllLines(envFile)) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
						continue;
					}
					int idx = line.indexOf('=');
					String key = line.substring(0, idx).trim();
					String val = line.substring(idx + 1).trim();
					if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
						if (val.length() >= 2) {
							val = val.substring(1, val.length() - 1);
						}
					}
					if (System.getProperty(key) == null && System.getenv(key) == null) {
						System.setProperty(key, val);
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

}
