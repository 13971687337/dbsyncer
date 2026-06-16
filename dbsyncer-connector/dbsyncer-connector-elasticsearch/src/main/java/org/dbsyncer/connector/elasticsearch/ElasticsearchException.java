/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.elasticsearch;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class ElasticsearchException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ElasticsearchException(String message) {
        super(message);
    }

    public ElasticsearchException(Throwable cause) {
        super(cause);
    }

}
