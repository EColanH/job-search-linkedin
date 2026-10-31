package dev.jobpilot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresPersistenceTest {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", postgres::getJdbcUrl);
    p.add("spring.datasource.username", postgres::getUsername);
    p.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired Store store;
  @Autowired JdbcTemplate jdbc;

  @Test
  void flywayJsonbAndCanonicalDedupPersist() {
    var a =
        store.importJob(
            "linkedin:synthetic", Map.of("title", "Synthetic vacancy", "availability", "UNKNOWN"));
    var b = store.importJob("linkedin:synthetic", Map.of("title", "Duplicate"));
    assertEquals(a.get("id"), b.get("id"));
    assertEquals(
        "Synthetic vacancy",
        jdbc.queryForObject(
            "select data->>'title' from domain_records where id=?", String.class, a.get("id")));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where success=true", Integer.class));
  }
}
