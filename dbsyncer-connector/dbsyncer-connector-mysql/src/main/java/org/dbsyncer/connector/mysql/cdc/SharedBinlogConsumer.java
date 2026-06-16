/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.cdc;

import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import org.dbsyncer.connector.mysql.MySQLException;
import org.dbsyncer.connector.mysql.binlog.BinaryLogClient;
import org.dbsyncer.connector.mysql.binlog.BinaryLogRemoteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 共享Binlog消费者 —— 多Mapping复用单binlog连接。
 *
 * <p>当多个Mapping指向同一个MySQL源（host:port/database）时，本类维护一条共享的
 * BinaryLogRemoteClient 连接，所有Mapping的 EventListener/LifecycleListener 注册在
 * 同一连接上，由单线程读取 + 多Map分发。 连接关闭时自动从全局注册表中移除。</p>
 *
 * <p>位点管理： 启动时取所有已注册Mapping的最低 snapshot 位点，保证不丢数据。
 * 通过 {@link #acknowledge} 跟踪各Mapping消费进度，暴露 minWatermark 供外部查询。</p>
 *
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-17 14:25
 */
public class SharedBinlogConsumer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** 全局共享消费者注册表，key = host:port/database */
    private static final Map<String, SharedBinlogConsumer> INSTANCES = new ConcurrentHashMap<>();

    private final String key;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String database;

    private BinaryLogRemoteClient client;
    private final Lock connectLock = new ReentrantLock();
    private volatile boolean started = false;

    /** 共享的表映射事件缓存（tableId -> TableMapEventData） */
    private final Map<Long, TableMapEventData> tableMapEventByTableId = new HashMap<>();

    /** 已注册的Mapping句柄：metaId -> MappingHandle */
    private final Map<String, MappingHandle> mappings = new ConcurrentHashMap<>();

    /** 位点水位线：metaId -> acknowledged position */
    private final Map<String, Long> watermarks = new ConcurrentHashMap<>();

    /** 全局最小位点水位线（所有Mapping中最低的已确认位点） */
    private volatile long minWatermark = 0;

    /**
     * 获取或创建共享消费者实例。
     *
     * @param host     源MySQL主机
     * @param port     源MySQL端口
     * @param username 源MySQL账号
     * @param password 源MySQL密码
     * @param database 源数据库名
     * @return 共享消费者实例
     */
    public static SharedBinlogConsumer getOrCreate(String host, int port, String username, String password, String database) {
        String key = host + ":" + port + "/" + database;
        return INSTANCES.computeIfAbsent(key, k -> {
            try {
                return new SharedBinlogConsumer(key, host, port, username, password, database);
            } catch (IOException e) {
                throw new MySQLException("创建SharedBinlogConsumer失败: " + e.getMessage(), e);
            }
        });
    }

    private SharedBinlogConsumer(String key, String host, int port, String username, String password, String database)
            throws IOException {
        this.key = key;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
        this.client = new BinaryLogRemoteClient(host, port, database, username, password, 0L);
        // 将共享的tableMap缓存挂载到客户端，所有监听器通过client.getTableMapEventByTableId()共享同一引用
        client.setTableMapEventByTableId(tableMapEventByTableId);
    }

    /**
     * 注册一个Mapping到共享消费者。
     * <p>实例化时即把事件监听器和生命周期监听器挂载到共享客户端上。
     * 实际连接在 {@link #start()} 中统一建立。</p>
     *
     * @param metaId           Mapping的Meta ID，用于去重和反注册
     * @param eventListener    事件监听器
     * @param lifecycleListener 生命周期监听器
     * @param snapshot         Mapping当前的快照（顺序型位点：fileName/position）
     */
    public void register(String metaId,
                         BinaryLogRemoteClient.EventListener eventListener,
                         BinaryLogRemoteClient.LifecycleListener lifecycleListener,
                         Map<String, String> snapshot) {
        mappings.put(metaId, new MappingHandle(eventListener, lifecycleListener, snapshot));
        client.registerEventListener(eventListener);
        client.registerLifecycleListener(lifecycleListener);
        logger.info("注册Mapping到共享消费者: metaId={}, key={}, 当前注册数={}", metaId, key, mappings.size());
    }

    /**
     * 从共享消费者中注销一个Mapping。
     * <p>当所有Mapping都注销后，关闭连接并从全局注册表中移除。</p>
     *
     * @param metaId Mapping的Meta ID
     */
    public void unregister(String metaId) {
        mappings.remove(metaId);
        watermarks.remove(metaId);
        logger.info("注销Mapping: metaId={}, key={}, 剩余注册数={}", metaId, key, mappings.size());
        if (mappings.isEmpty()) {
            close();
            INSTANCES.remove(key);
            logger.info("共享binlog连接已关闭: key={}", key);
        }
    }

    /**
     * 设置启动快照位点（取最小位点策略）。
     * <p>多次调用会取所有已注册Mapping中最小的位点（同一binlog文件取最小position，
     * 不同binlog文件取字典序最小的文件）。必须在 {@link #start()} 之前调用。</p>
     *
     * @param fileName binlog文件名
     * @param position binlog位点
     */
    public void setStartupSnapshot(String fileName, long position) {
        if (client.getBinlogFilename() == null) {
            client.setBinlogFilename(fileName);
            client.setBinlogPosition(position);
        } else if (fileName.equals(client.getBinlogFilename()) && position < client.getBinlogPosition()) {
            client.setBinlogPosition(position);
        } else if (fileName.compareTo(client.getBinlogFilename()) < 0) {
            client.setBinlogFilename(fileName);
            client.setBinlogPosition(position);
        }
    }

    /**
     * 启动共享binlog连接。
     * <p>需要提前通过 {@link #register} 注册所有Mapping的监听器。
     * 重复调用安全（幂等）。</p>
     *
     * @throws Exception 连接失败时抛出
     */
    public synchronized void start() throws Exception {
        if (started) {
            return;
        }

        client.connect();
        started = true;
        logger.info("共享binlog连接已建立: key={}, host={}:{}/{}", key, host, port, database);
    }

    /**
     * 关闭共享连接。
     */
    public void close() {
        try {
            connectLock.lock();
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            started = false;
        } catch (Exception e) {
            logger.error("关闭共享binlog连接失败:{}", e.getMessage());
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * 确认某个Mapping已消费到位点position。
     *
     * @param metaId   Mapping的Meta ID
     * @param position 已消费的binlog位点
     */
    public void acknowledge(String metaId, long position) {
        watermarks.put(metaId, position);
        minWatermark = watermarks.values().stream().min(Long::compare).orElse(0L);
    }

    public BinaryLogClient getClient() {
        return client;
    }

    public Map<Long, TableMapEventData> getTableMapEventByTableId() {
        return tableMapEventByTableId;
    }

    public boolean isStarted() {
        return started;
    }

    public long getMinWatermark() {
        return minWatermark;
    }

    public String getKey() {
        return key;
    }

    public int getMappingCount() {
        return mappings.size();
    }

    /**
     * JVM关闭时清理所有共享消费者实例
     */
    public static void shutdownAll() {
        INSTANCES.forEach((key, consumer) -> {
            try {
                consumer.close();
                consumer.logger.info("SharedBinlogConsumer已关闭: {}", key);
            } catch (Exception e) {
                consumer.logger.error("关闭SharedBinlogConsumer失败: {}", key, e);
            }
        });
        INSTANCES.clear();
    }

    /**
     * Mapping注册句柄。
     */
    private static class MappingHandle {
        final BinaryLogRemoteClient.EventListener eventListener;
        final BinaryLogRemoteClient.LifecycleListener lifecycleListener;
        final Map<String, String> snapshot;

        MappingHandle(BinaryLogRemoteClient.EventListener el,
                      BinaryLogRemoteClient.LifecycleListener ll,
                      Map<String, String> snap) {
            this.eventListener = el;
            this.lifecycleListener = ll;
            this.snapshot = snap;
        }
    }
}
