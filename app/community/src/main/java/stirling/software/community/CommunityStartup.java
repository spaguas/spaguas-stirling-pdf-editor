package stirling.software.community;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import stirling.software.common.model.ApplicationProperties;

/**
 * A Community build always authenticates; old server settings cannot silently disable its security.
 */
@Component
public class CommunityStartup implements ApplicationRunner {
    private final CommunityAccounts accounts;
    private final CommunityProperties properties;
    private final CommunityLegacyImport migration;

    public CommunityStartup(
            CommunityAccounts accounts,
            CommunityProperties properties,
            ApplicationProperties application,
            CommunityLegacyImport migration) {
        this.accounts = accounts;
        this.properties = properties;
        this.migration = migration;
        application.getSecurity().setEnableLogin(true);
    }

    @Override
    public void run(ApplicationArguments arguments) throws java.sql.SQLException {
        migration.runIfConfigured();
        accounts.bootstrap(properties);
    }
}
