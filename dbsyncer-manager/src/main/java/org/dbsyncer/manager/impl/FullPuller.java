/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.manager.impl;

import org.dbsyncer.common.util.NumberUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.manager.AbstractPuller;
import org.dbsyncer.parser.LogService;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.ParserComponent;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.enums.ParserEnum;
import org.dbsyncer.parser.event.FullRefreshEvent;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.parser.model.Task;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全量同步
 *
 * @Version 1.0.0
 * @Author AE86
 * @Date 2020-04-26 15:28
 */
@Component
public final class FullPuller extends AbstractPuller implements ApplicationListener<FullRefreshEvent> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ParserComponent parserComponent;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private LogService logService;

    private final Map<String, Task> map = new ConcurrentHashMap<>();

    @Override
    public void start(Mapping mapping) {
        List<TableGroup> list = profileComponent.getSortedTableGroupAll(mapping.getId());
        Assert.notEmpty(list, "映射关系不能为空");
        Thread worker = new Thread(() -> {
            final String metaId = mapping.getMetaId();
            ExecutorService executor = Executors.newFixedThreadPool(mapping.getThreadNum());
            try {
                Task task = map.computeIfAbsent(metaId, k -> new Task(metaId));
                logger.info("开始全量同步：{}, {}", metaId, mapping.getName());
                doTask(task, mapping, list, executor);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                logService.log(LogType.SystemLog.ERROR, e.getMessage());
            } finally {
                try {
                    executor.shutdown();
                } catch (Exception e) {
                    logService.log(LogType.SystemLog.ERROR, e.getMessage());
                }
                map.remove(metaId);
                publishClosedEvent(metaId);
                logger.info("结束全量同步：{}, {}", metaId, mapping.getName());
            }
        });
        worker.setName("full-worker-" + mapping.getId());
        worker.setDaemon(false);
        worker.start();
    }

    @Override
    public void close(String metaId) {
        map.computeIfPresent(metaId, (k, task) -> {
            task.stop();
            return null;
        });
    }

    @Override
    public void onApplicationEvent(FullRefreshEvent event) {
        // 异步监听任务刷新事件
        flush(event.getTask());
    }

    private void doTask(Task task, Mapping mapping, List<TableGroup> list, Executor executor) {
        long now = Instant.now().toEpochMilli();
        task.setBeginTime(now);
        task.setEndTime(now);

        Meta meta = profileComponent.getMeta(task.getId());
        Map<String, String> snapshot = meta.getSnapshot();
        task.setPageIndex(NumberUtil.toInt(snapshot.get(ParserEnum.PAGE_INDEX.getCode()), ParserEnum.PAGE_INDEX.getDefaultValue()));
        task.setCursors(PrimaryKeyUtil.getLastCursors(snapshot.get(ParserEnum.CURSOR.getCode())));
        task.setTableGroupIndex(NumberUtil.toInt(snapshot.get(ParserEnum.TABLE_GROUP_INDEX.getCode()), ParserEnum.TABLE_GROUP_INDEX.getDefaultValue()));
        flush(task);

        int parallelism = Math.max(1, Math.min(mapping.getThreadNum(), list.size()));
        if (parallelism > 1) {
            doTaskParallel(task, mapping, list, executor, parallelism);
        } else {
            doTaskSequential(task, mapping, list, executor);
        }

        task.setEndTime(Instant.now().toEpochMilli());
        task.setTableGroupIndex(ParserEnum.TABLE_GROUP_INDEX.getDefaultValue());
        flush(task);
    }

    private void doTaskSequential(Task task, Mapping mapping, List<TableGroup> list, Executor executor) {
        int i = task.getTableGroupIndex();
        while (i < list.size()) {
            parserComponent.execute(task, mapping, list.get(i), executor);
            if (!task.isRunning()) {
                break;
            }
            task.setPageIndex(ParserEnum.PAGE_INDEX.getDefaultValue());
            task.setCursors(null);
            task.setTableGroupIndex(++i);
            flush(task);
        }
    }

    private void doTaskParallel(Task task, Mapping mapping, List<TableGroup> list, Executor executor, int parallelism) {
        int startIndex = task.getTableGroupIndex();
        int remaining = list.size() - startIndex;
        int batchSize = Math.max(1, remaining / parallelism + (remaining % parallelism > 0 ? 1 : 0));

        logger.info("并行全量同步: metaId={}, tables={}, parallelism={}, batchSize={}",
                task.getId(), remaining, parallelism, batchSize);

        AtomicInteger completedCount = new AtomicInteger(0);
        for (int batch = 0; batch < parallelism && task.isRunning(); batch++) {
            final int batchStart = startIndex + batch * batchSize;
            final int batchEnd = Math.min(batchStart + batchSize, list.size());
            if (batchStart >= batchEnd) break;

            executor.execute(() -> {
                Task subTask = new Task(task.getId() + "-batch-" + batchStart);
                subTask.setPageIndex(task.getPageIndex());
                subTask.setCursors(task.getCursors());
                try {
                    for (int i = batchStart; i < batchEnd && task.isRunning(); i++) {
                        parserComponent.execute(subTask, mapping, list.get(i), executor);
                        subTask.setPageIndex(ParserEnum.PAGE_INDEX.getDefaultValue());
                        subTask.setCursors(null);
                        subTask.setTableGroupIndex(i);
                    }
                } catch (Exception e) {
                    logger.error("并行同步批次[{}]异常: {}", batchStart, e.getMessage(), e);
                } finally {
                    completedCount.incrementAndGet();
                }
            });
        }

        // 等待所有并行批次完成
        while (task.isRunning() && completedCount.get() < Math.min(parallelism, remaining)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (task.isRunning()) {
            task.setTableGroupIndex(list.size());
            task.setPageIndex(ParserEnum.PAGE_INDEX.getDefaultValue());
            task.setCursors(null);
            flush(task);
        }
    }

    private void flush(Task task) {
        Meta meta = profileComponent.getMeta(task.getId());
        Assert.notNull(meta, "检查meta为空.");

        // 全量的过程中，有新数据则更新总数
        long finished = meta.getSuccess().get() + meta.getFail().get();
        if (meta.getTotal().get() < finished) {
            meta.getTotal().set(finished);
        }

        meta.setBeginTime(task.getBeginTime());
        meta.setEndTime(task.getEndTime());
        meta.setUpdateTime(Instant.now().toEpochMilli());
        Map<String, String> snapshot = meta.getSnapshot();
        snapshot.put(ParserEnum.PAGE_INDEX.getCode(), String.valueOf(task.getPageIndex()));
        snapshot.put(ParserEnum.CURSOR.getCode(), StringUtil.getIfBlank(StringUtil.join(task.getCursors(), StringUtil.COMMA), StringUtil.EMPTY));
        snapshot.put(ParserEnum.TABLE_GROUP_INDEX.getCode(), String.valueOf(task.getTableGroupIndex()));
        profileComponent.editConfigModel(meta);
    }

}