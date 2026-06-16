/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.sqlite;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class SQLiteException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SQLiteException(String message) {
        super(message);
    }

    public SQLiteException(String message, Throwable cause) {
        super(message, cause);
    }

    public SQLiteException(Throwable cause) {
        super(cause);
    }

    protected SQLiteException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
