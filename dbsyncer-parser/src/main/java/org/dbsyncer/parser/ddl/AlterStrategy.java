/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.parser.ddl;

import net.sf.jsqlparser.statement.alter.AlterExpression;
import org.dbsyncer.sdk.config.DDLConfig;

/**
 * Alter策略
 *
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public interface AlterStrategy {

    /**
     * 解析DDLConfig
     *
     * @param expression
     * @param ddlConfig
     */
    void parse(AlterExpression expression, DDLConfig ddlConfig);
}
