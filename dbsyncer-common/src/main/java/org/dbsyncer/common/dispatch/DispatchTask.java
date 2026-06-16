/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.common.dispatch;

import org.dbsyncer.common.enums.DispatchTaskEnum;

import java.util.function.Consumer;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface DispatchTask extends Runnable {

    /**
     * 唯一任务id
     */
    String getUniqueId();

    DispatchTaskEnum getType();

    void destroy();

    void onDestroy(Consumer<DispatchTask> consumer);
}