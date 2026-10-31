package dev.jobpilot;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@Service
class Worker {
 private final RestClient client;
 Worker(@Value("${jobpilot.worker-url}")String url){var f=new org.springframework.http.client.SimpleClientHttpRequestFactory();f.setConnectTimeout(5000);f.setReadTimeout(60000);client=RestClient.builder().baseUrl(url).requestFactory(f).build();}
 Map<String,Object> parse(MultipartFile file){try{String name=Objects.toString(file.getOriginalFilename(),"");if(file.isEmpty()||file.getSize()>10*1024*1024||!name.toLowerCase().matches(".*\\.(pdf|docx)$"))throw new ApiError(400,"Upload a PDF or DOCX up to 10 MB");var body=new LinkedMultiValueMap<String,Object>();body.add("file",new ByteArrayResource(file.getBytes()){@Override public String getFilename(){return name.replaceAll("[^a-zA-Z0-9._-]","_");}});return response(client.post().uri("/parse").contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(Map.class));}catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(502,"Document extraction failed. Ensure the worker is running and the file is a valid, unencrypted PDF or DOCX.");}}
 Map<String,Object> generate(Map<String,Object> body){try{return response(client.post().uri("/generate").body(body).retrieve().body(Map.class));}catch(Exception e){throw new ApiError(502,"Document generation failed. Check worker health and verified candidate facts.");}}
 byte[] download(String id,String format){UUID.fromString(id);if(!Set.of("pdf","docx","original").contains(format))throw new ApiError(400,"Invalid document format");try{return client.get().uri("/documents/{id}/{format}",id,format).retrieve().body(byte[].class);}catch(Exception e){throw new ApiError(502,"Document unavailable; restore the document storage backup if needed.");}}
 void deleteAll(){try{client.delete().uri("/documents").retrieve().toBodilessEntity();}catch(Exception e){throw new ApiError(502,"Document worker unavailable. No database records deleted; retry when worker is healthy.");}}
 @SuppressWarnings("unchecked") private Map<String,Object> response(Map m){return m==null?Map.of():(Map<String,Object>)m;}
}
