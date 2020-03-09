package test.pivotal.pal.trackerapi;

import com.jayway.jsonpath.DocumentContext;
import io.pivotal.pal.tracker.PalTrackerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static com.jayway.jsonpath.JsonPath.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = PalTrackerApplication.class, webEnvironment = RANDOM_PORT)
public class HealthApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void healthTest() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        DocumentContext healthJson = parse(response.getBody());

        assertThat(healthJson.read("$.status", String.class)).isEqualTo("UP");
        assertThat(healthJson.read("$.components.diskSpace.status", String.class)).isEqualTo("UP");
        assertThat(healthJson.read("$.components.ping.status", String.class)).isEqualTo("UP");
        assertThat(healthJson.read("$.components.flywayMigration.status", String.class)).isEqualTo("UP");
    }

    @Test
    public void readinessProbeTest() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        DocumentContext healthJson = parse(response.getBody());

        assertThat(healthJson.read("$.status", String.class)).isEqualTo("UP");
    }

    @Test
    public void livenessProbeTest() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("/actuator/health/liveness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        DocumentContext healthJson = parse(response.getBody());

        assertThat(healthJson.read("$.status", String.class)).isEqualTo("UP");
    }
}