package dev.jobpilot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CandidateUpdate(
    @Size(max = 200) String name,
    @Size(max = 5000) String summary,
    @Valid @Size(max = 150) List<Fact> verifiedFacts,
    @Valid @Size(max = 500) List<Fact> pendingFacts,
    @Size(max = 50) List<@Size(max = 500) String> careerGoals,
    @Size(max = 300) String location,
    @Size(max = 3) List<@Pattern(regexp = "REMOTE|HYBRID|ON_SITE") String> workArrangements,
    @DecimalMin("0") BigDecimal preferredSalary) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Fact(
      @Size(max = 100) String id,
      @NotBlank @Size(max = 100) String section,
      @NotBlank @Size(max = 5000) String text) {}
}
