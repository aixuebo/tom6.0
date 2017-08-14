package maming.filter;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  // Although the internal implementation uses a Map, this cache
  // implementation is only concerned with the keys.
  private final Map<T,T> cache;
  
  public LruCache(final int cacheSize) {
      cache = new LinkedHashMap<T,T>() {
          private static final long serialVersionUID = 1L;
          @Override
          protected boolean removeEldestEntry(Map.Entry<T,T> eldest) {
            System.out.println("ssss");
              if (size() > cacheSize) {
                  return true;
              }
              return false;
          }
      };
  }
  
  public void add(T key) {
      synchronized (cache) {
          cache.put(key, null);
      }
  }

  public boolean contains(T key) {
      synchronized (cache) {
          return cache.containsKey(key);
      }
  }
  
  public int size(){
    return cache.size();
  }
  
  public String str(){
    return cache.toString();
  }
  
  
  public static void main(String[] args) {
    LruCache lru = new LruCache<String>(5);
    lru.add("aa");
    lru.add("bb");
    lru.add("cc");
    lru.add("dd");
    lru.add("ee");
    lru.add("ff");
    lru.add("hh");
    lru.add("ii");
    lru.add("jj");
    System.out.println(lru.size());
    System.out.println(lru.str());
  }
  
}
