package br.com.reciclathe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReciclatheApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReciclatheApplication.class, args);
    }
}
