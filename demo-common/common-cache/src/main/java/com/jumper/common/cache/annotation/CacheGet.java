package com.jumper.common.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 获取缓存数据
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheGet {

    /**
     * 缓存的key
     * @return
     */
    String key();

    /**
     * 缓存的包含条件（符合该条件的数据会从缓存中查询）
     * @return
     */
    String condition() default "";

    /**
     * 缓存的排除条件（符合该条件的数据不会从缓存中查询，优先级高于condition）
     * @return
     */
    String unless() default "";

    /**
     * 缓存的过期时间，默认300秒
     * @return
     */
    long timeout() default 300;

    /**
     * 是否随机缓存过期时间，默认不随机，一旦指定该属性，则timeout会失效
     * @return
     */
    boolean isRandomTimeout() default false;

    /**
     * 最大的随机时间1天，isRandomTimeout为true时生效
     * @return
     */
    long maxTimeout() default 60*60*24;

    /**
     * 最小的随机时间100秒，isRandomTimeout为true时生效
     * @return
     */
    long minTimeout() default 100;

    /**
     * 缓存相关时间单位，默认为秒
     * @return
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}
