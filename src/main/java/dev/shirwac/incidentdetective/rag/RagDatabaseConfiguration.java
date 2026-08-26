package dev.shirwac.incidentdetective.rag;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
@Profile("rag")
public class RagDatabaseConfiguration {

    @Bean(destroyMethod = "close")
    HikariDataSource ragDataSource(RagDatabaseProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("incident-detective-rag");
        config.setJdbcUrl(properties.url());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(properties.maximumPoolSize());
        config.setMinimumIdle(0);
        config.setConnectionTimeout(properties.connectionTimeoutMs());
        return new HikariDataSource(config);
    }

    @Bean
    Flyway ragFlyway(HikariDataSource ragDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(ragDataSource)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    @DependsOn("ragFlyway")
    JdbcClient ragJdbcClient(HikariDataSource ragDataSource) {
        return JdbcClient.create(ragDataSource);
    }
}
