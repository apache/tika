package org.apache.tika.pipes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class TikaPipesApplication {
	public static void main(String[] args) {
		SpringApplication.run(TikaPipesApplication.class, args);
	}
}
