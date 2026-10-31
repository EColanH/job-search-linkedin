package dev.jobpilot.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
/** Available source facts only; optional fields remain unknown rather than inferred. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobImportRequest(
 @NotBlank @Size(max=2000) String url,
 @Size(max=200) String title,
 @Size(max=300) String company,
 @Size(max=300) String location,
 @Size(max=100000) String description,
 @Size(max=50) String workArrangement,
 @Size(max=100) String employmentType,
 @Size(max=100) String seniority,
 @Size(max=200) String industry,
 @DecimalMin("0") BigDecimal salaryMin,
 @DecimalMin("0") BigDecimal salaryMax,
 @Pattern(regexp="[A-Z]{3}") String currency,
 @Pattern(regexp="MONTHLY|YEARLY|HOURLY") String salaryPeriod,
 @Size(max=50) String publishedAt,
 @Size(max=100) List<@Size(max=5000) String> responsibilities,
 @Size(max=100) List<@Size(max=5000) String> requirements,
 @Size(max=100) List<@Size(max=5000) String> preferredQualifications,
 @Size(max=100) List<@Size(max=5000) String> benefits,
 Map<String,Object> jobPoster
){}
