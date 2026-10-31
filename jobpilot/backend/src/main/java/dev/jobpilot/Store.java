package dev.jobpilot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class Store {
 private final RecordRepository repo;
 Store(RecordRepository repo){this.repo=repo;}
 public Map<String,Object> get(String kind,String id){var e=repo.findById(id).filter(v->v.kind.equals(kind)).orElseThrow(()->new ApiError(404,"Record not found"));return map(e);}
 public List<Map<String,Object>> list(String kind){return repo.findByKindOrderByCreatedAtDesc(kind).stream().map(this::map).toList();}
 @Transactional public Map<String,Object> put(String kind,String id,Map<String,Object> data){var e=repo.findById(id).orElseGet(()->new RecordEntity(kind,id,data));if(!e.kind.equals(kind))throw new ApiError(409,"ID conflict");e.data=new LinkedHashMap<>(data);return map(repo.saveAndFlush(e));}
 @Transactional public Map<String,Object> create(String kind,Map<String,Object> data){return put(kind,UUID.randomUUID().toString(),data);}
 @Transactional public Map<String,Object> importJob(String source,Map<String,Object> data){var old=repo.findBySourceKey(source);if(old.isPresent())return map(old.get());var e=new RecordEntity("job",UUID.randomUUID().toString(),data);e.sourceKey=source;return map(repo.saveAndFlush(e));}
 @Transactional public void deleteAll(){repo.deleteAllInBatch();}
 private Map<String,Object> map(RecordEntity e){var m=new LinkedHashMap<>(e.data);m.put("id",e.id);m.put("createdAt",e.createdAt.toString());return m;}
}
