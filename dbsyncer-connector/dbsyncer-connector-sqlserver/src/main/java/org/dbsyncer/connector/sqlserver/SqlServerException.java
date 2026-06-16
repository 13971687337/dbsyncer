/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.sqlserver;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class SqlServerException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SqlServerException(String message) {
        super(message);
    }

    public SqlServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public SqlServerException(Throwable cause) {
        super(cause);
    }

    protected SqlServerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
