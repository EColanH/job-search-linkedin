package dev.jobpilot;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecordRepository extends JpaRepository<RecordEntity, String> {
  List<RecordEntity> findByKindOrderByCreatedAtDesc(String kind);

  Optional<RecordEntity> findBySourceKey(String sourceKey);

  void deleteByKind(String kind);
}
