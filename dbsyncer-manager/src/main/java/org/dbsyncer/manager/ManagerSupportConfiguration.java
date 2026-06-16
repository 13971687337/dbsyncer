/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.manager;

import org.dbsyncer.manager.deployment.StandaloneProvider;
import org.dbsyncer.sdk.spi.DeploymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhangxl
 * @version 1.0.0
 * @Date 2026-06-02 14:25
 */
@Configuration
public class ManagerSupportConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeploymentService deploymentService() {
        return new StandaloneProvider();
    }

}