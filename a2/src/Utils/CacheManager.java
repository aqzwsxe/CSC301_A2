package Utils;

import java.util.concurrent.ConcurrentHashMap;

public class CacheManager<K, V> {
    private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();

    public V get(K key){
        return cache.get(key);
    }

    public void put(K key, V value){
        cache.put(key, value);
    }

    public void  invalidate(K key){
        cache.remove(key);
    }

    public void clear(){
        cache.clear();
    }

}
