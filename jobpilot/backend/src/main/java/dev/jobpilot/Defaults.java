package dev.jobpilot;

import java.util.*;

final class Defaults {
  static Map<String, Object> candidate() {
    return new LinkedHashMap<>(
        Map.of(
            "name",
            "",
            "summary",
            "",
            "verifiedFacts",
            List.of(),
            "pendingFacts",
            List.of(),
            "documents",
            List.of()));
  }

  static Map<String, Object> preferences() {
    var m = new LinkedHashMap<String, Object>();
    m.put(
        "titles",
        Arrays.stream(
                new String[] {
                  "Customer Success Manager",
                  "Senior Customer Success Specialist",
                  "Client Operations Specialist",
                  "Senior Client Operations Specialist",
                  "Business Operations Specialist",
                  "PMO Specialist",
                  "Project Coordinator",
                  "Associate Project Manager",
                  "Implementation Coordinator",
                  "Customer Experience Specialist",
                  "Customer Support Team Lead",
                  "Client Onboarding Specialist"
                })
            .map(t -> Map.of("title", t, "enabled", true))
            .toList());
    m.put("city", "Kraków");
    m.put("country", "Poland");
    m.put("workArrangements", List.of("REMOTE", "HYBRID", "ON_SITE"));
    m.put("publicationDays", 30);
    for (String k :
        List.of(
            "keywords",
            "excludedCompanies",
            "excludedKeywords",
            "companies",
            "industries",
            "seniority",
            "employmentTypes")) m.put(k, List.of());
    m.put("minMatchScore", 0);
    m.put("salaryFilterEnabled", false);
    m.put("preferredSalary", 15000);
    m.put("salaryMin", null);
    m.put("salaryMax", null);
    m.put("currency", "PLN");
    m.put("salaryPeriod", "MONTHLY");
    m.put("includeUndisclosedSalaries", true);
    m.put("scheduleEnabled", false);
    return m;
  }

  static Map<String, Object> settings() {
    return new LinkedHashMap<>(
        Map.of(
            "ai",
            Map.of(
                "provider",
                "OLLAMA",
                "model",
                "llama3.2",
                "temperature",
                0.2,
                "externalConsent",
                false),
            "matchingWeights",
            Map.of("experience", 35, "skills", 30, "seniority", 15, "growth", 10, "location", 10),
            "integration",
            Map.of(
                "status",
                "MANUAL_ONLY",
                "authorizedDiscovery",
                false,
                "message",
                "Automatic LinkedIn discovery is unavailable. Use search links and manual imports."
                    + " No scraping, messages, or applications are automated.")));
  }
}
