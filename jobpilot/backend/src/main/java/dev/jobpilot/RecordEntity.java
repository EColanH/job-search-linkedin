package dev.jobpilot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "domain_records",
    indexes = {@Index(name = "records_kind_idx", columnList = "kind")})
class RecordEntity {
  @Id String id;

  @Column(nullable = false)
  String kind;

  @Column(name = "source_key", unique = true)
  String sourceKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  Map<String, Object> data = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  Instant createdAt = Instant.now();

  @Version long version;

  protected RecordEntity() {}

  RecordEntity(String kind, String id, Map<String, Object> data) {
    this.kind = kind;
    this.id = id;
    this.data = new LinkedHashMap<>(data);
  }
}
