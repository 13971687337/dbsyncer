/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.command;

import org.dbsyncer.parser.ParserException;

/**
 * 序列化接口
 *
 * @Version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public interface Persistence {

    default boolean addConfig() {
        throw new ParserException("Unsupported method addConfig");
    }

    default boolean editConfig() {
        throw new ParserException("Unsupported method editConfig");
    }
}