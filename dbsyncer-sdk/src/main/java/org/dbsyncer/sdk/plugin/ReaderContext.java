/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.sdk.plugin;

import org.dbsyncer.sdk.model.Table;

import java.util.List;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface ReaderContext extends BaseContext {

    Table getSourceTable();

    boolean isSupportedCursor();

    List<Object> getArgs();

    Object[] getCursors();

    int getPageIndex();

    int getPageSize();

}