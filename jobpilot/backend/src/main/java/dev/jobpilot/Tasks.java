package dev.jobpilot;
import org.springframework.stereotype.Service;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
@Service
public class Tasks {
 private final Store store;
 private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(20),new ThreadPoolExecutor.AbortPolicy());
 Tasks(Store store){this.store=store;}
 @PostConstruct void recover(){for(var t:store.list("task"))if(Set.of("QUEUED","RUNNING").contains(t.get("status"))){t.put("status","FAILED");t.put("error","Interrupted by restart; retry operation");store.put("task",t.get("id").toString(),t);}}
 public synchronized Map<String,Object> submit(String type,Supplier<Object> action){var task=store.create("task",new LinkedHashMap<>(Map.of("type",type,"status","QUEUED")));String id=task.get("id").toString();try{executor.execute(()->{var t=new LinkedHashMap<>(task);t.put("status","RUNNING");store.put("task",id,t);try{t.put("result",action.get());t.put("status","COMPLETED");}catch(Exception e){t.put("status","FAILED");t.put("error",e instanceof ApiError?e.getMessage():"Operation failed; check worker or AI service availability.");}store.put("task",id,t);});}catch(RejectedExecutionException e){task.put("status","FAILED");task.put("error","Task queue is full; retry later");store.put("task",id,task);throw new ApiError(429,"Task queue is full; retry later");}return task;}
 public synchronized void withoutActiveTasks(Runnable action){if(executor.getActiveCount()>0||!executor.getQueue().isEmpty())throw new ApiError(409,"Wait for active tasks to finish before deleting data");action.run();}
 @PreDestroy void shutdown(){executor.shutdownNow();}
}
