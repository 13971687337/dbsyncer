/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.common.dispatch;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface DispatchTaskService {

    void execute(DispatchTask task);

    void stop(String uniqueId);

    boolean isRunning(String uniqueId);
}