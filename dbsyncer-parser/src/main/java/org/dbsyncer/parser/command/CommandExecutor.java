/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.command;

/**
 * @Version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public interface CommandExecutor {

    Object execute(Command cmd);
}