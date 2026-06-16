/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz;

import org.dbsyncer.biz.vo.VersionVo;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface AppConfigService {

    /**
     * 获取版本信息
     *
     * @return
     */
    VersionVo getVersionInfo(String username);
}