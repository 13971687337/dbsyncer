/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 持久化配置（JDK21 虚拟线程）
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2023/8/28 23:50
 */
@Configuration
@ConfigurationProperties(prefix = "dbsyncer.storage")
public class StorageConfig extends BufferActuatorConfig {

    @Bean(name = "storageExecutor", destroyMethod = "shutdown")
    public ExecutorService storageExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}