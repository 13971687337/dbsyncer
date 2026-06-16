/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.postgresql;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class PostgreSQLException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PostgreSQLException(String message) {
        super(message);
    }

    public PostgreSQLException(String message, Throwable cause) {
        super(message, cause);
    }

    public PostgreSQLException(Throwable cause) {
        super(cause);
    }

    protected PostgreSQLException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
