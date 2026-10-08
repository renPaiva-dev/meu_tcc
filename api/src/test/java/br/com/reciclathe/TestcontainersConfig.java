package br.com.reciclathe;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL com PostGIS real: as consultas espaciais não podem ser testadas em banco em memória. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgis() {
        return new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:16-3.5")
                .asCompatibleSubstituteFor("postgres"));
    }
}
