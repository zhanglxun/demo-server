package com.jumper.common.cache.annotation;

import java.lang.annotation.*;

/**
 * 删除缓存
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheDelete {

    /**
     * 需要删除的key
     *
     * @return
     */
    String[] key() default "";

    /**
     * 是否删除所有缓存信息，key参数会失效
     *
     * @return
     */
    boolean isDeleteAll() default false;

    /**
     * 删除缓存的包含条件（符合该条件的数据会从缓存中删除）
     *
     * @return
     */
    String condition() default "";

    /**
     * 删除缓存的排除条件（符合该条件的数据不会从缓存中删除，优先级高于condition）
     *
     * @return
     */
    String unless() default "";

    /**
     * 是否为前置删除（在执行业务之前删除缓存，默认为后置删除）
     *
     * @return
     */
    boolean beforeInvocation() default false;

    /**
     * 是否通过正则表达式删除key
     *
     * @return
     */
    boolean deletePattern() default false;
}
