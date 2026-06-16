/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector;

import org.dbsyncer.sdk.model.Table;

import java.util.List;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface ConnectorServiceContext {

    String getCatalog();

    String getSchema();

    List<Table> getTablePatterns();
}