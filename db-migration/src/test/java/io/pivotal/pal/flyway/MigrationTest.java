package io.pivotal.pal.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MigrateDevPalTrackerDbApp.class)
public class MigrationTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void testMigration() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<Migration> migrationList = jdbcTemplate
                .query("select version, description from flyway_schema_history", mapper);

        assertThat(migrationList.get(0).version).isEqualTo("1.0.1");
        assertThat(migrationList.get(0).description).isEqualTo("time-entries");
    }

    private final RowMapper<Migration> mapper = (rs, rowNum) -> new Migration(
            rs.getString("version"),
            rs.getString("description")
    );

    static class Migration {
        public String version;
        public String description;

        public Migration(String version, String description) {
            this.version = version;
            this.description = description;
        }
    }
}
