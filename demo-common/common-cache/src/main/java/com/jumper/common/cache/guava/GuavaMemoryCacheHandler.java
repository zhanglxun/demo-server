package com.jumper.common.cache.guava;

import cn.hutool.core.util.ReUtil;
import com.google.common.cache.Cache;
import com.jumper.common.cache.handler.MemoryCacheHandler;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 基于JVM内存的缓存策略
 */
public class GuavaMemoryCacheHandler<K extends Serializable, V> extends MemoryCacheHandler<K, V> {

    @Autowired
    @Qualifier(value = "guavaCache")
    private Cache cache;
    @Value("${jumper.cache.maximumMemory:50}")
    private Integer maximumMemory;
    @Autowired
    private Executor executor;

    @Override
    public boolean putCache(K key, V value) {
        return this.putCache(key, value, -1L, TimeUnit.SECONDS);
    }

    @Override
    public boolean putCache(K key, V value, Long timeOut, TimeUnit unit) {
        if (!isMaximumMemory()) {
            return true;
        }
        CacheValue cacheValue = new CacheValue()
                .setValue(value)
                .setCreateTime(System.currentTimeMillis())
                .setTimeout(timeOut)
                .setUnit(unit);
        cache.put(key, cacheValue);
        return true;
    }

    @Override
    public V getCache(K key) {
        CacheValue cacheValue = (CacheValue) cache.getIfPresent(key);

        if (cacheValue != null) {
            Long timeout = cacheValue.getTimeout();
            //如果为-1表示永生
            if (timeout == -1)
                return (V) cacheValue.getValue();

            //判断当前是否超时
            if (cacheValue.getCreateTime() + cacheValue.getUnit().toMillis(timeout) < System.currentTimeMillis()) {
                //已经超时
                deleteCache(key);
            } else {
                //未超时
                return (V) cacheValue.getValue();
            }
        }
        return null;
    }

    @Override
    public boolean deleteCache(K key) {
        cache.invalidate(key);
        return true;
    }

    /**
     * 通过正则表达式删除key
     *
     * @param key
     * @return
     */
    @Override
    public boolean deleteCachePattern(K key) {
        CompletableFuture.runAsync(() -> {
            String patten = String.valueOf(key);
            List<String> keys = new ArrayList<>();
            //取出缓存中的key
            ConcurrentMap concurrentMap = cache.asMap();
            concurrentMap.keySet().forEach(cacheKey -> {
                String deleteKey = String.valueOf(cacheKey);
                //通过正则校验，缓存中的key与指定key是否匹配,匹配则放入list中
                boolean match = ReUtil.contains(patten, deleteKey);
                if (match) {
                    keys.add(deleteKey);
                }
            });
            //删除匹配到的所有key
            if (keys.size() > 0) {
                cache.invalidateAll(keys);
            }
        }, executor);
        return true;
    }

    /**
     * 缓存对象
     *
     * @param <V>
     */
    @Data
    @Accessors(chain = true)
    private static class CacheValue<V> {
        //缓存的值
        V value;
        //超时时间
        Long timeout;
        //单位
        TimeUnit unit;
        //创建时间
        Long createTime;
    }

    /**
     * 判断JVM剩余内存是否到达指定的最大内存
     *
     * @return
     */
    private boolean isMaximumMemory() {
        //JVM剩余内存
        long freeMemory = Runtime.getRuntime().freeMemory();
        return freeMemory > maximumMemory * 1024 * 1024;
    }
}
