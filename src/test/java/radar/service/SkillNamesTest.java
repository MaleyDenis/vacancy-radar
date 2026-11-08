package radar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillNamesTest {

  @Test
  void stripsVersions() {
    assertThat(SkillNames.canonical("C++17")).isEqualTo("C++");
    assertThat(SkillNames.canonical("Java 21")).isEqualTo("Java");
    assertThat(SkillNames.canonical("Java 11+")).isEqualTo("Java");
    assertThat(SkillNames.canonical("Spring Boot 3")).isEqualTo("Spring Boot");
    assertThat(SkillNames.canonical("Python 3.12")).isEqualTo("Python");
    assertThat(SkillNames.canonical("Angular v17")).isEqualTo("Angular");
  }

  @Test
  void keepsNamesWhereTheNumberIsPartOfTheName() {
    assertThat(SkillNames.canonical("AWS S3")).isEqualTo("AWS S3");
    assertThat(SkillNames.canonical("EC2")).isEqualTo("EC2");
    assertThat(SkillNames.canonical("OAuth2")).isEqualTo("OAuth2");
    assertThat(SkillNames.canonical("Log4j")).isEqualTo("Log4j");
  }

  @Test
  void mergesSynonyms() {
    assertThat(SkillNames.canonical("Apache Kafka")).isEqualTo("Kafka");
    assertThat(SkillNames.canonical("postgres")).isEqualTo("PostgreSQL");
    assertThat(SkillNames.canonical("RESTful APIs")).isEqualTo("REST");
    assertThat(SkillNames.clean(List.of("Kafka", "Apache Kafka", "K8s", "Kubernetes")))
        .containsExactly("Kafka", "Kubernetes");
  }

  @Test
  void dropsJunkAndDuplicates() {
    assertThat(SkillNames.clean(List.of("C++", "C++17", "C++20", "Jira", "Confluence", "Git",
        "Agile", "Databases", "PostgreSQL", "java", "Java 21")))
        .containsExactly("C++", "PostgreSQL", "java");
  }
}
