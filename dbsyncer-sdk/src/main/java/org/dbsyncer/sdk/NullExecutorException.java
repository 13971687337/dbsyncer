/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class NullExecutorException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public NullExecutorException(String message) {
        super(message);
    }
}