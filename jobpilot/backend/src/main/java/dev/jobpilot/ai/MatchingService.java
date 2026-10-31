package dev.jobpilot.ai;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Explainable, conservative evidence matching. Compensation never changes suitability. */
@Service
public class MatchingService {
  private final AiService ai;

  public MatchingService(AiService ai) {
    this.ai = ai;
  }

  private static final LinkedHashMap<String, List<String>> TERMS = new LinkedHashMap<>();

  static {
    TERMS.put("customer success", List.of("customer success"));
    TERMS.put("customer support", List.of("customer support", "customer service"));
    TERMS.put(
        "client communication",
        List.of("client communication", "customer communication", "communicating with clients"));
    TERMS.put(
        "escalation management", List.of("escalation management", "escalations", "escalation"));
    TERMS.put(
        "issue resolution", List.of("issue resolution", "problem resolution", "resolving issues"));
    TERMS.put("cross-functional collaboration", List.of("cross-functional", "cross functional"));
    TERMS.put("onboarding", List.of("onboarding"));
    TERMS.put("implementation", List.of("implementation"));
    TERMS.put("project management", List.of("project management"));
    TERMS.put("project coordination", List.of("project coordination", "project coordinator"));
    TERMS.put("stakeholder management", List.of("stakeholder management"));
    TERMS.put("operations", List.of("operations", "operational support"));
    TERMS.put("customer experience", List.of("customer experience"));
    TERMS.put(
        "people management", List.of("people management", "line management", "managing a team"));
    TERMS.put(
        "financial analysis",
        List.of("financial analysis", "financial modeling", "financial modelling"));
    TERMS.put("PMP certification", List.of("pmp"));
    TERMS.put("PMI certification", List.of("pmi"));
    TERMS.put("SQL", List.of("sql"));
    TERMS.put("Excel", List.of("excel"));
    TERMS.put("Salesforce", List.of("salesforce"));
    TERMS.put("Jira", List.of("jira"));
    TERMS.put("Agile", List.of("agile"));
    TERMS.put("English", List.of("english"));
    TERMS.put("Polish", List.of("polish"));
  }

  public Map<String, Object> analyze(
      Map<String, Object> candidate, Map<String, Object> job, Map<String, Object> settings) {
    List<Map<String, Object>> facts = AiService.facts(candidate);
    String description = Objects.toString(job.get("description"), "");
    String jobText = norm(Objects.toString(job.get("title"), "") + " " + description);
    Map<String, Integer> weights = weights(settings);
    List<Map<String, Object>> evidence = new ArrayList<>();
    List<String> needed =
        TERMS.entrySet().stream()
            .filter(e -> e.getValue().stream().anyMatch(t -> contains(jobText, t)))
            .map(Map.Entry::getKey)
            .toList();
    Set<String> matched = new LinkedHashSet<>();
    Set<String> experienceFactIds = new HashSet<>();
    for (var fact : facts) {
      String text = norm(fact.get("text").toString());
      String section = fact.get("section").toString().toLowerCase(Locale.ROOT);
      for (String term : needed) {
        if (TERMS.get(term).stream().anyMatch(t -> contains(text, t))
            && !negated(text, TERMS.get(term))) {
          if ((section.contains("course") || section.contains("education"))
              && List.of(
                      "project management",
                      "PMP certification",
                      "PMI certification",
                      "people management")
                  .contains(term)) continue;
          matched.add(term);
          if (section.contains("experience")
              || section.contains("employment")
              || section.contains("achievement")) experienceFactIds.add(fact.get("id").toString());
          evidence.add(
              Map.of(
                  "factId",
                  fact.get("id"),
                  "candidateEvidence",
                  fact.get("text"),
                  "jobTerm",
                  term,
                  "source",
                  "VERIFIED_PROFILE_AND_JOB"));
        }
      }
    }
    List<String> missing =
        needed.stream()
            .filter(t -> !matched.contains(t))
            .map(t -> "Not evidenced in the verified profile: " + t)
            .toList();
    Map<String, Integer> components = new LinkedHashMap<>();
    long experienceCount =
        facts.stream()
            .filter(
                f ->
                    f.get("section")
                        .toString()
                        .toLowerCase(Locale.ROOT)
                        .matches(".*(experience|employment|achievement).*"))
            .count();
    components.put(
        "experience",
        experienceCount == 0
            ? 0
            : (int) Math.round(100.0 * experienceFactIds.size() / experienceCount));
    components.put(
        "skills", needed.isEmpty() ? 0 : (int) Math.round(100.0 * matched.size() / needed.size()));
    components.put("seniority", seniorityScore(facts, jobText));
    components.put("growth", growthScore(candidate, description));
    components.put("location", locationScore(candidate, job));
    boolean insufficient = facts.isEmpty() || description.trim().length() < 40 || needed.isEmpty();
    int score =
        (int)
            Math.round(
                components.entrySet().stream()
                    .mapToDouble(e -> e.getValue() * weights.get(e.getKey()) / 100.0)
                    .sum());
    var selection =
        insufficient
            ? new AiService.Selection(List.of(), "NOT_REQUESTED", AiService.PROMPT_VERSION)
            : ai.selectFacts(candidate, job, settings);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("score", insufficient ? null : score);
    result.put(
        "classification",
        insufficient
            ? "Insufficient Information"
            : score >= 80
                ? "Strong Match"
                : score >= 65 ? "Good Match" : score >= 45 ? "Stretch Opportunity" : "Low Match");
    result.put("engine", "EVIDENCE_HEURISTIC_V1");
    result.put(
        "methodology",
        "Conservative phrase overlap against user-verified facts. Scores are decision support, not"
            + " a prediction. AI selects existing fact IDs only and cannot change the score. Salary"
            + " is evaluated separately.");
    result.put("weights", weights);
    result.put("components", components);
    result.put("evidence", evidence);
    result.put(
        "matchingExperience",
        evidence.stream().map(e -> e.get("candidateEvidence")).distinct().toList());
    result.put("transferableSkills", new ArrayList<>(matched));
    result.put("missingQualifications", missing);
    result.put(
        "concerns",
        insufficient
            ? List.of(
                "Add verified experience and a complete job description before relying on this"
                    + " analysis.")
            : List.of(
                "Phrase overlap does not verify proficiency, duration, seniority or formal"
                    + " qualifications. Review the complete vacancy requirements."));
    result.put(
        "whySuitable",
        matched.isEmpty()
            ? "No supported overlap identified."
            : "Verified profile facts overlap with the vacancy in: "
                + String.join(", ", matched)
                + ". See the evidence before deciding.");
    result.put(
        "seniorityAssessment",
        components.get("seniority") > 0
            ? "An explicit seniority term overlaps; scope and years still need manual review."
            : "Seniority alignment is not established by the verified facts.");
    result.put(
        "careerDevelopmentPotential",
        components.get("growth") > 0
            ? "The posting explicitly mentions development that overlaps with stated career"
                + " preferences."
            : "Career progression is not established; clarify during the interview.");
    result.put("salaryAlignment", salary(job, candidate));
    result.put(
        "recommendation",
        insufficient
            ? "Verify the candidate profile and supply the full LinkedIn job description."
            : score >= 65
                ? "Review the evidence and missing qualifications before shortlisting."
                : "Review the gaps before investing in an application.");
    result.put("aiStatus", selection.status());
    result.put("aiRelevantFactIds", selection.factIds());
    result.put("promptVersion", selection.promptVersion());
    result.put("createdAt", java.time.Instant.now().toString());
    return result;
  }

  private static Map<String, Integer> weights(Map<String, Object> settings) {
    Map<String, Integer> defaults =
        Map.of("experience", 35, "skills", 30, "seniority", 15, "growth", 10, "location", 10);
    Object raw = settings.get("matchingWeights");
    if (!(raw instanceof Map<?, ?> map)) return defaults;
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    for (String key : defaults.keySet()) {
      Object value = map.get(key);
      if (!(value instanceof Number n)
          || n.intValue() < 0
          || n.intValue() > 100
          || n.doubleValue() != n.intValue())
        throw new IllegalArgumentException("Matching weights must be integers from 0 to 100");
      result.put(key, n.intValue());
    }
    if (result.values().stream().mapToInt(Integer::intValue).sum() != 100)
      throw new IllegalArgumentException("Matching weights must total 100");
    return result;
  }

  private static int seniorityScore(List<Map<String, Object>> facts, String job) {
    for (String term : List.of("senior", "junior", "associate", "team lead", "director", "head of"))
      if (contains(job, term))
        return facts.stream()
                .filter(
                    f ->
                        f.get("section")
                            .toString()
                            .toLowerCase(Locale.ROOT)
                            .matches(".*(experience|employment).*"))
                .anyMatch(f -> contains(norm(f.get("text").toString()), term))
            ? 100
            : 0;
    return 0;
  }

  private static int growthScore(Map<String, Object> candidate, String description) {
    String goals = norm(Objects.toString(candidate.get("careerGoals"), ""));
    String job = norm(description);
    return (contains(goals, "growth") || contains(goals, "progression"))
            && (contains(job, "career development")
                || contains(job, "career progression")
                || contains(job, "professional development"))
        ? 100
        : 0;
  }

  private static int locationScore(Map<String, Object> candidate, Map<String, Object> job) {
    String location = norm(Objects.toString(candidate.get("location"), ""));
    String jobLocation = norm(Objects.toString(job.get("location"), ""));
    if (!location.isBlank() && !jobLocation.isBlank() && contains(jobLocation, location))
      return 100;
    Object arrangements = candidate.get("workArrangements");
    return arrangements instanceof List<?> list
            && list.stream()
                .anyMatch(
                    a ->
                        a.toString()
                            .equalsIgnoreCase(
                                Objects.toString(job.get("workArrangement"), "UNKNOWN")))
        ? 100
        : 0;
  }

  private static Map<String, Object> salary(
      Map<String, Object> job, Map<String, Object> candidate) {
    if (!(job.get("salaryMin") instanceof Number) && !(job.get("salaryMax") instanceof Number))
      return Map.of("status", "NOT_DISCLOSED", "explanation", "Salary not disclosed.");
    if (Objects.toString(job.getOrDefault("salarySource", "ADVERTISED"))
        .equalsIgnoreCase("ESTIMATED"))
      return Map.of(
          "status",
          "ESTIMATE_EXCLUDED",
          "explanation",
          "An estimate is not advertised compensation and is excluded from alignment and"
              + " filtering.");
    String preferredCurrency = Objects.toString(candidate.get("preferredCurrency"), "PLN");
    String preferredPeriod = Objects.toString(candidate.get("preferredSalaryPeriod"), "MONTHLY");
    if (!Objects.toString(job.get("currency"), "").equalsIgnoreCase(preferredCurrency)
        || !Objects.toString(job.get("salaryPeriod"), "").equalsIgnoreCase(preferredPeriod))
      return Map.of(
          "status",
          "NOT_COMPARABLE",
          "explanation",
          "Currency or salary period differs from the configured preference; no conversion or"
              + " estimate is applied.");
    double preferred =
        candidate.get("preferredSalary") instanceof Number n ? n.doubleValue() : 15000;
    double maximum =
        job.get("salaryMax") instanceof Number n
            ? n.doubleValue()
            : ((Number) job.get("salaryMin")).doubleValue();
    return Map.of(
        "status",
        maximum >= preferred ? "POTENTIALLY_ALIGNED" : "BELOW_PREFERENCE",
        "explanation",
        "Advertised compensation compared with the configured preference. Verify gross/net basis"
            + " and contract terms; this never changes professional suitability.");
  }

  private static boolean negated(String text, List<String> variants) {
    for (String variant : variants) {
      if (Pattern.compile(
              "(?<![a-z0-9])(no|not|without)\\s+(?:[a-z]+\\s+){0,3}"
                  + Pattern.quote(norm(variant))
                  + "(?![a-z0-9])")
          .matcher(text)
          .find()) return true;
    }
    return false;
  }

  private static boolean contains(String text, String term) {
    return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(norm(term)) + "(?![a-z0-9])")
        .matcher(text)
        .find();
  }

  private static String norm(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replace('ł', 'l')
        .replaceAll("\\s+", " ")
        .trim();
  }
}
