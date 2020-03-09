package io.pivotal.pal.tracker.instrumentation;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FlywayMigrationHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbcTemplate;
    private final InfoEndpoint infoEndpoint;

    public FlywayMigrationHealthIndicator(DataSource dataSource,
                                          InfoEndpoint infoEndpoint) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.infoEndpoint = infoEndpoint;
    }

    private boolean verifyMigrations() {
        String minorVersionFilter = getMinorVersionFilter();

        try {
            Integer migrationCount = jdbcTemplate.queryForObject(
                    "select count(version) from flyway_schema_history where version like ?",
                    Integer.class,
                    minorVersionFilter);
            return migrationCount > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String getMinorVersionFilter() {
        String appVersion = ((Map<String, String>)infoEndpoint.info().get("build")).get("version");

        Pattern versionPattern = Pattern.compile("([1-9]\\d*)\\.(\\d+)\\.(\\d+)");
        Matcher matcher = versionPattern.matcher(appVersion);

        return matcher.matches() ?
                matcher.group(1) + "." + matcher.group(2) + "%" :
                "";
    }

    @Override
    public Health health() {
        return verifyMigrations() ?
                Health.up().build() :
                Health.down().build();
    }
}
