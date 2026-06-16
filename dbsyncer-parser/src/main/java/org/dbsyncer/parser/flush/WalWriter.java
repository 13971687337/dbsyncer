/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush;

import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.parser.model.WalEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * WAL写入器
 * <p>将binlog事件以JSON行格式追加写入本地WAL文件。
 * 写入目标端成功后通过commit标记该条目已完成。</p>
 * <p>设计要点：
 * <ul>
 *   <li>JSON行格式：每行一个JSON，方便恢复时逐行解析</li>
 *   <li>追加写入：顺序IO，性能优异</li>
 *   <li>读写锁保护：保证多线程写的线程安全</li>
 *   <li>每次写入立即flush：保证持久性（durability）</li>
 * </ul>
 *
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-16
 */
public class WalWriter implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final File walFile;
    private final BufferedWriter writer;
    private final AtomicLong sequence = new AtomicLong(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public WalWriter(String walDir, String metaId) throws IOException {
        Files.createDirectories(Paths.get(walDir));
        this.walFile = new File(walDir, metaId + ".wal");
        // 追加模式打开，确保不覆盖已有记录
        this.writer = new BufferedWriter(new FileWriter(walFile, true));
        logger.info("WAL文件已创建: {}", walFile.getAbsolutePath());
    }

    /**
     * 追加一条事件到WAL（写入目标端之前调用）
     *
     * @param tableName     源表名
     * @param event         事件类型（INSERT/UPDATE/DELETE）
     * @param rowData       变更的行数据
     * @param binlogFile    binlog文件名
     * @param binlogPosition binlog位点
     * @return WAL条目
     */
    public WalEntry append(String tableName, String event, List<Object> rowData,
                           String binlogFile, long binlogPosition) throws IOException {
        WalEntry entry = new WalEntry();
        entry.setSequence(sequence.incrementAndGet());
        entry.setTableName(tableName);
        entry.setEvent(event);
        entry.setRowData(rowData);
        entry.setBinlogFile(binlogFile);
        entry.setBinlogPosition(binlogPosition);
        entry.setCommitted(false);

        lock.writeLock().lock();
        try {
            String json = JsonUtil.objToJson(entry);
            writer.write(json);
            writer.newLine();
            writer.flush(); // 确保持久化到磁盘
        } finally {
            lock.writeLock().unlock();
        }
        return entry;
    }

    /**
     * 标记WAL条目已提交（写入目标端成功后调用）
     *
     * @param seq 要标记为已提交的序号
     */
    public void commit(long seq) {
        lock.writeLock().lock();
        try {
            // 写入提交标记行，JSON格式 {"commit":<seq>}
            writer.write("{\"commit\":" + seq + "}");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            logger.error("写入WAL提交标记失败, seq={}", seq, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取WAL文件绝对路径
     */
    public String getWalFilePath() {
        return walFile.getAbsolutePath();
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            writer.close();
            logger.info("WAL文件已关闭: {}", walFile.getAbsolutePath());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
