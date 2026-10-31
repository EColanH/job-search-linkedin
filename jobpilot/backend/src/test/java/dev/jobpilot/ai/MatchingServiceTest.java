package dev.jobpilot.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class MatchingServiceTest {
  final AiService ai = new AiService(new ObjectMapper());
  final MatchingService matching = new MatchingService(ai);
  final Map<String, Object> settings = Map.of("ai", Map.of("provider", "DISABLED"));

  Map<String, Object> candidate(String section, String text) {
    return Map.of("verifiedFacts", List.of(Map.of("id", "f1", "section", section, "text", text)));
  }

  Map<String, Object> job() {
    return new HashMap<>(
        Map.of(
            "title",
            "Customer Operations Specialist",
            "description",
            "Handle customer support, escalation management and operations. PMP certification is"
                + " required for this vacancy."));
  }

  @Test
  void noVerifiedProfileIsInsufficient() {
    var r = matching.analyze(Map.of(), job(), settings);
    assertNull(r.get("score"));
    assertEquals("Insufficient Information", r.get("classification"));
  }

  @Test
  void employerNameDoesNotImplyFinance() {
    var r =
        matching.analyze(
            candidate("experience", "Worked at Synthetic Bank"),
            Map.of(
                "title",
                "Financial Analyst",
                "description",
                "Responsible for financial analysis and financial modeling for the business."),
            settings);
    assertEquals(List.of(), r.get("matchingExperience"));
    assertEquals(0, r.get("score"));
  }

  @Test
  void courseDoesNotImplyCertificationOrProfessionalExperience() {
    var r =
        matching.analyze(
            candidate(
                "courses", "Project management course in progress. No PMP or PMI certification."),
            Map.of(
                "title",
                "Project Manager",
                "description",
                "Project management experience and PMP certification are required."),
            settings);
    assertEquals(List.of(), r.get("matchingExperience"));
    assertTrue(
        ((List<?>) r.get("missingQualifications"))
            .stream().anyMatch(s -> s.toString().contains("PMP")));
  }

  @Test
  void salaryCannotChangeProfessionalScore() {
    var c = candidate("experience", "Customer support, escalation management and operations.");
    var a = job();
    a.put("salaryMin", 9000);
    a.put("currency", "PLN");
    a.put("salaryPeriod", "MONTHLY");
    var b = job();
    b.put("salaryMin", 22000);
    b.put("currency", "PLN");
    b.put("salaryPeriod", "MONTHLY");
    var ar = matching.analyze(c, a, settings);
    var br = matching.analyze(c, b, settings);
    assertEquals(ar.get("score"), br.get("score"));
    assertNotEquals(ar.get("salaryAlignment"), br.get("salaryAlignment"));
  }

  @Test
  void evidenceCitesOnlyVerifiedFactsAndKeepsMissingRequirements() {
    var r =
        matching.analyze(
            candidate("experience", "Customer support and operations."), job(), settings);
    assertEquals("DISABLED", r.get("aiStatus"));
    assertTrue(
        ((List<?>) r.get("missingQualifications"))
            .stream().anyMatch(s -> s.toString().contains("PMP")));
    assertTrue(
        ((List<?>) r.get("evidence"))
            .stream().allMatch(v -> ((Map<?, ?>) v).get("factId").equals("f1")));
    assertTrue((Integer) r.get("score") < 80);
  }

  @Test
  void invalidWeightsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            matching.analyze(
                Map.of(), job(), Map.of("matchingWeights", Map.of("experience", 100))));
  }

  @Test
  void structuredOutputCannotAddNewFacts() throws Exception {
    var f = AiService.facts(candidate("experience", "Customer support."));
    assertEquals(List.of("f1"), ai.validateSelection("{\"factIds\":[\"f1\"]}", f));
    assertThrows(
        IllegalArgumentException.class,
        () -> ai.validateSelection("{\"factIds\":[\"invented\"]}", f));
    assertThrows(
        IllegalArgumentException.class,
        () -> ai.validateSelection("{\"factIds\":[],\"newExperience\":\"manager\"}", f));
  }

  @Test
  void externalProviderRequiresConsentBeforeRequest() {
    assertEquals(
        "CONFIGURATION_OR_OUTPUT_REJECTED",
        ai.selectFacts(
                candidate("experience", "Customer support"),
                job(),
                Map.of("ai", Map.of("provider", "OPENAI-COMPATIBLE", "externalConsent", false)))
            .status());
  }

  @Test
  void salaryAlignmentUsesConfiguredCurrencyAndPeriod() {
    var c =
        new HashMap<String, Object>(candidate("experience", "Customer support and operations."));
    c.put("preferredSalary", 70000);
    c.put("preferredCurrency", "EUR");
    c.put("preferredSalaryPeriod", "YEARLY");
    var j = job();
    j.putAll(
        Map.of(
            "salaryMin",
            60000,
            "salaryMax",
            80000,
            "salarySource",
            "ADVERTISED",
            "currency",
            "EUR",
            "salaryPeriod",
            "YEARLY"));
    var aligned = matching.analyze(c, j, settings);
    assertEquals("POTENTIALLY_ALIGNED", ((Map<?, ?>) aligned.get("salaryAlignment")).get("status"));
    j.put("currency", "PLN");
    var other = matching.analyze(c, j, settings);
    assertEquals("NOT_COMPARABLE", ((Map<?, ?>) other.get("salaryAlignment")).get("status"));
    assertEquals(aligned.get("score"), other.get("score"));
  }
}
