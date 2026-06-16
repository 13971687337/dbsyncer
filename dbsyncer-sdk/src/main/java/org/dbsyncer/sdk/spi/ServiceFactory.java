/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.sdk.spi;

/**
 * SPI服务工厂，集中管理，提高加载效率
 *
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface ServiceFactory {

    <T> T get(Class<T> serviceClass);

}