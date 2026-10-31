package dev.jobpilot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface RecordRepository extends JpaRepository<RecordEntity,String>{
 List<RecordEntity> findByKindOrderByCreatedAtDesc(String kind);
 Optional<RecordEntity> findBySourceKey(String sourceKey);
 void deleteByKind(String kind);
}
