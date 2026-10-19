package stirling.software.community;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

import stirling.software.common.service.LicenseServiceInterface;

/** Community identity persists independently of commercial-edition tables and signing keys. */
@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(CommunityProperties.class)
public class CommunityConfiguration {
    @Bean
    DataSource communityDataSource(CommunityProperties properties) throws IOException {
        Path directory = Path.of(properties.getDataDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        }
        String url =
                properties.getDatabaseUrl().isBlank()
                        ? "jdbc:h2:file:"
                                + directory.resolve("identity")
                                + ";DB_CLOSE_ON_EXIT=FALSE"
                        : properties.getDatabaseUrl();
        if (!url.startsWith("jdbc:h2:")) {
            throw new IllegalArgumentException(
                    "Community identity requires a dedicated H2 database");
        }
        HikariDataSource source = new HikariDataSource();
        source.setJdbcUrl(url);
        source.setUsername("sa");
        source.setPassword("");
        source.setMaximumPoolSize(4);
        source.setPoolName("community-identity");
        new ResourceDatabasePopulator(new ClassPathResource("community-schema.sql"))
                .execute(source);
        return source;
    }

    @Bean
    JdbcTemplate communityJdbcTemplate(DataSource source) {
        return new JdbcTemplate(source);
    }

    @Bean
    PlatformTransactionManager communityTransactionManager(DataSource source) {
        return new DataSourceTransactionManager(source);
    }

    @Bean
    PasswordEncoder communityPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    Clock communityClock() {
        return Clock.systemUTC();
    }

    @Bean
    LicenseServiceInterface communityCapabilities() {
        return new LicenseServiceInterface() {
            @Override
            public String getLicenseTypeName() {
                return "COMMUNITY";
            }

            @Override
            public boolean isRunningProOrHigher() {
                return false;
            }

            @Override
            public boolean hasServerLicense() {
                return false;
            }

            @Override
            public boolean isRunningEE() {
                return false;
            }
        };
    }
}
