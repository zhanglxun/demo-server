package com.jumper.common.cache.handler;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * 基础数据缓存操作规范
 */
public interface BaseCacheHandler<K extends Serializable, V> {

    /**
     * 添加缓存
     *
     * @param key
     * @param value
     * @return
     */
    boolean putCache(K key, V value);

    /**
     * 添加缓存并且携带超时时间
     *
     * @param key
     * @param value
     * @param timeOut
     * @param unit
     * @return
     */
    boolean putCache(K key, V value, Long timeOut, TimeUnit unit);

    /**
     * 获取缓存
     *
     * @param key
     * @return
     */
    V getCache(K key);

    /**
     * 删除缓存
     *
     * @param key
     * @return
     */
    boolean deleteCache(K key);

    /**
     * 通过正则表达式删除key
     *
     * @param key
     * @return
     */
    boolean deleteCachePattern(K key);
}
