package dev.jobpilot;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;

@Service
public class LinkedIn {
  public Map<String, String> canonical(String value) {
    try {
      URI u = URI.create(value);
      String host = Objects.toString(u.getHost(), "").toLowerCase(Locale.ROOT);
      if (!"https".equals(u.getScheme())
          || !(host.equals("linkedin.com") || host.endsWith(".linkedin.com"))
          || u.getUserInfo() != null
          || u.getPort() != -1) throw new IllegalArgumentException();
      Matcher m = Pattern.compile("^/jobs/view/(?:[^/]*-)?(\\d+)/?$").matcher(u.getPath());
      if (!m.matches()) throw new IllegalArgumentException();
      return Map.of(
          "id", m.group(1), "url", "https://www.linkedin.com/jobs/view/" + m.group(1) + "/");
    } catch (Exception e) {
      throw new ApiError(
          400,
          "Use an HTTPS LinkedIn job URL such as https://www.linkedin.com/jobs/view/123456789/");
    }
  }

  public List<Map<String, String>> searches(Map<String, Object> p) {
    var links = new ArrayList<Map<String, String>>();
    for (var title : Input.maps(p.get("titles"))) {
      if (!Input.bool(title, "enabled")) continue;
      String t = Input.required(title, "title", 200);
      var params = new LinkedHashMap<String, String>();
      params.put("keywords", t + " " + String.join(" ", Input.strings(p.get("keywords"))));
      params.put("location", Input.text(p, "city") + ", " + Input.text(p, "country"));
      int days = (int) Input.num(p.get("publicationDays"), 30);
      if (days > 0) params.put("f_TPR", "r" + (Math.min(days, 365) * 86400));
      var modes =
          Input.strings(p.get("workArrangements")).stream()
              .map(
                  s ->
                      switch (s) {
                        case "ON_SITE" -> "1";
                        case "REMOTE" -> "2";
                        case "HYBRID" -> "3";
                        default -> "";
                      })
              .filter(s -> !s.isBlank())
              .toList();
      if (!modes.isEmpty()) params.put("f_WT", String.join(",", modes));
      var levels =
          Map.of(
              "INTERNSHIP",
              "1",
              "ENTRY_LEVEL",
              "2",
              "ASSOCIATE",
              "3",
              "MID_SENIOR",
              "4",
              "DIRECTOR",
              "5",
              "EXECUTIVE",
              "6");
      var employment =
          Map.of(
              "FULL_TIME",
              "F",
              "PART_TIME",
              "P",
              "CONTRACT",
              "C",
              "TEMPORARY",
              "T",
              "INTERNSHIP",
              "I",
              "VOLUNTEER",
              "V",
              "OTHER",
              "O");
      addCodes(params, "f_E", Input.strings(p.get("seniority")), levels);
      addCodes(params, "f_JT", Input.strings(p.get("employmentTypes")), employment);
      String query =
          params.entrySet().stream()
              .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
              .reduce((a, b) -> a + "&" + b)
              .orElse("");
      links.add(Map.of("title", t, "url", "https://www.linkedin.com/jobs/search/?" + query));
    }
    return links;
  }

  private void addCodes(
      Map<String, String> p, String key, List<String> values, Map<String, String> codes) {
    String v =
        values.stream()
            .map(codes::get)
            .filter(Objects::nonNull)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    if (!v.isBlank()) p.put(key, v);
  }

  static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
