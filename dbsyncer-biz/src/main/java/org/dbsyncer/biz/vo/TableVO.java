/**
 * DBSyncer Copyright 2020-2026 All Rights Reserved.
 */
package org.dbsyncer.biz.vo;

import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.sdk.model.Table;

/**
 * 表信息
 *
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public final class TableVO extends Table {

    public String getColumnJson() {
        return JsonUtil.objToJson(super.getColumn());
    }

    public String getExtInfoJson() {
        return JsonUtil.objToJson(super.getExtInfo());
    }
}