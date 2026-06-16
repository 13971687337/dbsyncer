package org.dbsyncer.parser;

/**
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public interface LogService {

    void log(LogType logType);

    void log(LogType logType, String msg);

    void log(LogType logType, String format, Object... args);
}