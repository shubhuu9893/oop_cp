package com.cachebrowse.database;

import com.cachebrowse.config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration holder for Cloudflare D1 Database.
 * Loads environment variables from the OS environment or from a local .env file.
 */
public class D1Config {
    private static final Logger LOG = LoggerFactory.getLogger(D1Config.class);

    private final String accountId;
    private final String apiToken;
    private final String databaseId;

    public D1Config() {
        boolean disabled = Boolean.parseBoolean(EnvConfig.get("CACHEBROWSE_DISABLE_D1"));
        this.accountId = disabled ? "" : EnvConfig.get("CLOUDFLARE_ACCOUNT_ID");
        this.apiToken = disabled ? "" : EnvConfig.get("CLOUDFLARE_API_TOKEN");
        this.databaseId = disabled ? "" : EnvConfig.get("D1_DATABASE_ID");

        LOG.info("D1 configuration presence: accountId={}, apiToken={}, databaseId={}",
                present(accountId), present(apiToken), present(databaseId));

        if (isConfigured()) {
            LOG.info("Cloudflare D1 configuration is complete; connectivity has not yet been verified.");
        } else if (disabled) {
            LOG.info("Cloudflare D1 is disabled by CACHEBROWSE_DISABLE_D1.");
        } else {
            LOG.warn("Cloudflare D1 is not fully configured (missing CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_API_TOKEN, or D1_DATABASE_ID). Operating in local fallback mode.");
        }
    }

    public D1Config(String accountId, String apiToken, String databaseId) {
        this.accountId = clean(accountId);
        this.apiToken = clean(apiToken);
        this.databaseId = clean(databaseId);
    }

    public boolean isConfigured() {
        return accountId != null && !accountId.isBlank() &&
               apiToken != null && !apiToken.isBlank() &&
               databaseId != null && !databaseId.isBlank();
    }

    public String getAccountId() {
        return accountId;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    private static String clean(String value) {
        if (value == null) return "";
        String result = value.trim();
        if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'")))) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }
}
