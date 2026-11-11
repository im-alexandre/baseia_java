package br.com.alexandre.java_ai_lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Inicialização da aplicação de ingestão normativa. */
@SpringBootApplication
public class BaseiaJavaApplication {
    /** Inicializa somente a infraestrutura Spring Boot e os serviços de ingestão. */
    public static void main(String[] args) {
        SpringApplication.run(BaseiaJavaApplication.class, args);
    }
}
