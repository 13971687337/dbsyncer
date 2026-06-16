package org.dbsyncer.parser.flush;

/**
 * @author zhangxl
 * @version 1.0.0
 * @date 2022/3/27 16:57
 */
public interface BufferRequest {

    /**
     * 获取驱动ID
     *
     * @return
     */
    String getMetaId();

    /**
     * 是否为栅栏标记（用于DDL事件在队列中插入屏障，消费端遇到时先清空累积数据再执行DDL）
     *
     * @return
     */
    default boolean isBarrier() {
        return false;
    }
}