package com.jumper.common.cache.handler;

import java.io.Serializable;

/**
 * 分布式缓存接口 - 二级缓存
 */
public abstract class ClusterCacheHandler<K extends Serializable, V>
        implements BaseCacheHandler<K, V> {
}
