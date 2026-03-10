package Utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CacheManager<K, V> {
    private static final String HOST = "127.0.0.1"; // run it locally for each machine
    private static final int PORT = 6379;
    private static final int TTL = 60;

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JedisPool pool;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(128);
        config.setMaxIdle(32);
        config.setMinIdle(8);

        pool = new JedisPool(config, HOST, PORT);
    }

    public V get(K key){
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key.toString());

            if (value == null)
                return null;

            return (V) mapper.readValue(value, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void put(K key, V value){
        try (Jedis jedis = pool.getResource()) {
            String json = mapper.writeValueAsString(value);
            jedis.setex(key.toString(), TTL, json);
        } catch (Exception ignored) {}
    }

    public void invalidate(K key){
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key.toString());
        }
    }

    public void clear(){
        try (Jedis jedis = pool.getResource()) {
            jedis.flushDB();
        }
    }
}
