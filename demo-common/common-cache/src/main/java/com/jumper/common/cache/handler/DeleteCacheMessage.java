package com.jumper.common.cache.handler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述：
 *
 * @author huangbin
 * @version 1.0
 * @date 2022/4/24 10:09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCacheMessage implements Serializable {
    private static final long serialVersionUID = -3444217464590115260L;
    private String key;
    private boolean deletePattern;
}
