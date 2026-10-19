package stirling.software.community;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

import stirling.software.common.configuration.InstallationPathConfig;

/**
 * Community-owned settings. Bootstrap credentials are needed only for an empty identity database.
 */
@Getter
@Setter
@ConfigurationProperties("community")
public class CommunityProperties {
    private String dataDirectory = InstallationPathConfig.getConfigPath() + "community";
    private String databaseUrl = "";
    private String initialAdminUsername = "admin";
    private String initialAdminPassword = "";
    private String legacyJdbcUrl = "";
    private String legacyUsername = "sa";
    private String legacyPassword = "";
}
