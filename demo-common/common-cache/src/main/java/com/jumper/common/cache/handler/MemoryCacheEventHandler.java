package com.jumper.common.cache.handler;

import com.jumper.common.event.apply.handle.EventHandler;
import com.jumper.common.event.apply.handle.annotation.EventType;
import com.jumper.common.event.framework.message.EventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
@Slf4j
@EventType("cache-delete")
public class MemoryCacheEventHandler implements EventHandler<DeleteCacheMessage> {

    @Autowired
    private MemoryCacheHandler memoryCacheHandler;

    @Override
    public void eventHandler(DeleteCacheMessage message, EventMessage eventMessage) {
        log.debug("接收到删除key：{}",message.getKey());
        boolean result = message.isDeletePattern() ? memoryCacheHandler.deleteCachePattern(message.getKey()) : memoryCacheHandler.deleteCache(message.getKey());

    }
}
