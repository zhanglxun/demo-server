package com.jumper.common.cache.redis;

import com.jumper.common.cache.handler.ClusterCacheHandler;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis实现的分布式缓存（二级缓存）
 *
 * @param <K>
 * @param <V>
 */
public class RedisClusterCacheHandler<K extends Serializable, V> extends ClusterCacheHandler<K, V> {

    /**
     * Redis模板
     */
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public boolean putCache(K key, V value) {
        //设置缓存不设置过期时间
        redisTemplate.opsForValue().set(key, value);
        return true;
    }

    @Override
    public boolean putCache(K key, V value, Long timeOut, TimeUnit unit) {
        //设置缓存并且设置过期时间
        redisTemplate.opsForValue().set(key, value, timeOut, unit);
        return true;
    }

    @Override
    public V getCache(K key) {
        return (V) redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean deleteCache(K key) {
        return redisTemplate.delete(key);
    }

    /**
     * 通过正则表达式删除key
     *
     * @param key
     * @return
     */
    @Override
    public boolean deleteCachePattern(K key) {
        redissonClient.getKeys().deleteByPatternAsync(String.valueOf(key));
        return true;
    }
}
