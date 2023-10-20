package com.jumper.common.cache.handler;

import com.jumper.common.event.apply.utils.EventUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;

/**
 * 内存缓存规范 - 一级缓存
 *
 * @param <K>
 * @param <V>
 */
public abstract class MemoryCacheHandler<K extends Serializable, V> implements BaseCacheHandler<K, V> {

    @Autowired(required = false)
    private EventUtils eventUtil;

    /**
     * 移除集群的内存缓存
     *
     * @param key
     * @return
     */
    public boolean deleteClusterCache(K key, boolean deletePattern) {
        if (eventUtil != null) {
            DeleteCacheMessage deleteCacheMessage = DeleteCacheMessage.builder().key(String.valueOf(key)).deletePattern(deletePattern).build();
            eventUtil.sendMsg("cache-delete", deleteCacheMessage, false);
            return true;
        }
        return false;
    }
}
