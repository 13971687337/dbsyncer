/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.filter;

/**
 * 值比较器实现
 *
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface CompareFilter {

    boolean compare(String value, String filterValue);

}