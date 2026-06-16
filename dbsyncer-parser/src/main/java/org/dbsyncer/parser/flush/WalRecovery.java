/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush;

import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.parser.model.WalEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * WAL恢复器
 * <p>启动时扫描WAL文件，找出所有未提交的记录，供重放使用。</p>
 * <p>恢复逻辑：
 * <ol>
 *   <li>逐行读取WAL文件</li>
 *   <li>遇到提交标记（{"commit":N}），记录该序号为已提交</li>
 *   <li>遇到数据行，如果其序号不在已提交集合中，则为未提交记录</li>
 *   <li>返回所有未提交记录，由调用方重放到目标端</li>
 *   <li>恢复完成后自动截断WAL文件（删除或重命名为.wal.recovered），避免重复恢复</li>
 * </ol>
 * <p>异常行（JSON解析失败）会被跳过并记录警告日志，保证恢复的鲁棒性。</p>
 *
 * @version 1.1.0
 * @Author zhangxl
 * @Date 2026-06-16
 */
public final class WalRecovery {

    private static final Logger logger = LoggerFactory.getLogger(WalRecovery.class);

    private WalRecovery() {
    }

    /**
     * 从WAL文件中恢复未提交的记录
     *
     * @param walDir WAL文件目录
     * @param metaId 驱动metaId
     * @return 恢复结果，包含统计信息和未提交条目列表
     */
    public static RecoveryResult recover(String walDir, String metaId) {
        File walFile = new File(walDir, metaId + ".wal");
        if (!walFile.exists()) {
            return new RecoveryResult(Collections.emptyList(), 0, 0, 0);
        }

        int corrupted = 0;
        Set<Long> committed = new HashSet<>();
        List<WalEntry> allEntries = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(walFile))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isEmpty()) {
                    continue;
                }

                // 提交标记行：{"commit":123}
                if (line.startsWith("{\"commit\":")) {
                    try {
                        long seq = extractCommitSeq(line);
                        committed.add(seq);
                    } catch (Exception e) {
                        corrupted++;
                        logger.warn("WAL恢复：第{}行提交标记解析失败: {}", lineNum, line);
                    }
                    continue;
                }

                // 数据行：WalEntry JSON
                try {
                    WalEntry entry = JsonUtil.jsonToObj(line, WalEntry.class);
                    if (entry != null && entry.getSequence() > 0) {
                        allEntries.add(entry);
                    }
                } catch (Exception e) {
                    corrupted++;
                    logger.warn("WAL恢复：第{}行JSON解析失败，跳过: {}", lineNum, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("WAL恢复：读取文件失败 {}", walFile.getAbsolutePath(), e);
            return new RecoveryResult(Collections.emptyList(), 0, 0, 0);
        }

        // 筛选未提交的记录
        List<WalEntry> uncommitted = new ArrayList<>();
        for (WalEntry entry : allEntries) {
            if (!committed.contains(entry.getSequence())) {
                uncommitted.add(entry);
            }
        }

        int replayed = uncommitted.size();
        int skipped = allEntries.size() - replayed;

        if (replayed > 0) {
            logger.info("WAL恢复：重放{}条，跳过{}条（已提交），损坏{}条，文件: {}",
                    replayed, skipped, corrupted, walFile.getName());
        } else {
            logger.info("WAL恢复：所有{}条已提交，损坏{}条，文件: {}",
                    allEntries.size(), corrupted, walFile.getName());
        }

        // 恢复完成后截断WAL文件，避免重复恢复
        truncateWalFile(walFile, walDir, metaId);

        return new RecoveryResult(uncommitted, replayed, skipped, corrupted);
    }

    /**
     * 截断WAL文件（删除；失败时重命名为.wal.recovered作为归档）
     */
    private static void truncateWalFile(File walFile, String walDir, String metaId) {
        try {
            java.nio.file.Files.deleteIfExists(walFile.toPath());
            logger.info("WAL文件已截断: {}", walFile.getName());
        } catch (IOException e) {
            // 删除失败时重命名为.recovered归档
            File recovered = new File(walDir, metaId + ".wal.recovered");
            if (walFile.renameTo(recovered)) {
                logger.info("WAL文件已归档: {}", recovered.getName());
            } else {
                logger.error("WAL文件截断失败: {}", walFile.getAbsolutePath(), e);
            }
        }
    }

    /**
     * 从提交标记行提取序号
     * 格式：{"commit":123}
     */
    private static long extractCommitSeq(String line) {
        int start = line.indexOf(":") + 1;
        int end = line.indexOf("}");
        return Long.parseLong(line.substring(start, end).trim());
    }

    /**
     * WAL恢复结果
     */
    public static class RecoveryResult {
        /** 未提交的WAL条目列表（将在binlog恢复时被重放） */
        private final List<WalEntry> uncommitted;
        /** 重放条数（未提交的条目数） */
        private final int replayed;
        /** 跳过条数（已提交的条目数） */
        private final int skipped;
        /** 损坏条数（JSON解析失败的条数） */
        private final int corrupted;

        public RecoveryResult(List<WalEntry> uncommitted, int replayed, int skipped, int corrupted) {
            this.uncommitted = uncommitted;
            this.replayed = replayed;
            this.skipped = skipped;
            this.corrupted = corrupted;
        }

        public List<WalEntry> getUncommitted() {
            return uncommitted;
        }

        public int getReplayed() {
            return replayed;
        }

        public int getSkipped() {
            return skipped;
        }

        public int getCorrupted() {
            return corrupted;
        }
    }
}
