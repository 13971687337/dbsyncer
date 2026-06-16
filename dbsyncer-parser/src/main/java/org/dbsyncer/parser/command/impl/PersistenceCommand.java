/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.command.impl;

import org.dbsyncer.parser.command.Command;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.storage.StorageService;

import java.util.Map;

/**
 * 配置序列化
 *
 * @Version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public final class PersistenceCommand implements Command {

    private StorageService storageService;

    private Map params;

    public PersistenceCommand(StorageService storageService, Map params) {
        this.storageService = storageService;
        this.params = params;
    }

    @Override
    public boolean addConfig() {
        storageService.add(StorageEnum.CONFIG, params);
        return true;
    }

    @Override
    public boolean editConfig() {
        storageService.edit(StorageEnum.CONFIG, params);
        return true;
    }

}