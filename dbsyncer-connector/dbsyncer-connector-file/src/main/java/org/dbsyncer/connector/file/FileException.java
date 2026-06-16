/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.file;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class FileException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public FileException(String message) {
        super(message);
    }

    public FileException(Throwable cause) {
        super(cause);
    }

}
