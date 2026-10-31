package dev.jobpilot;
import java.util.*;
final class Input {
 static String text(Map<String,Object> m,String k){return Objects.toString(m.get(k),"").strip();}
 static String required(Map<String,Object> m,String k,int max){String s=text(m,k);if(s.isEmpty()||s.length()>max)throw new ApiError(400,k+" is required and must be at most "+max+" characters");return s;}
 static double num(Object o,double fallback){return o instanceof Number n?n.doubleValue():fallback;}
 static boolean bool(Map<String,Object> m,String k){return Boolean.TRUE.equals(m.get(k));}
 @SuppressWarnings("unchecked") static List<Map<String,Object>> maps(Object o){if(o==null)return new ArrayList<>();if(!(o instanceof List<?> l)||l.size()>500)throw new ApiError(400,"Expected a list of at most 500 items");var result=new ArrayList<Map<String,Object>>();for(Object item:l){if(!(item instanceof Map<?,?>))throw new ApiError(400,"Invalid list item");result.add(new LinkedHashMap<>((Map<String,Object>)item));}return result;}
 @SuppressWarnings("unchecked") static Map<String,Object> map(Object o){return o instanceof Map<?,?>?(Map<String,Object>)o:Map.of();}
 static List<String> strings(Object o){if(o==null)return List.of();if(!(o instanceof List<?> l)||l.size()>100)throw new ApiError(400,"Expected at most 100 values");return l.stream().map(v->Objects.toString(v,"").strip()).filter(v->!v.isBlank()).toList();}
 static Map<String,Object> fields(Map<String,Object> source,String... allowed){var result=new LinkedHashMap<String,Object>();for(String k:allowed)if(source.containsKey(k))result.put(k,source.get(k));return result;}
}
