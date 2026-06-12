# 插件开发指南

DBSyncer 支持两种插件扩展：**自定义转换插件**（Plugin）和**自定义连接器**（Connector）。

## 一、自定义转换插件

转换插件在数据读取和写入之间执行自定义逻辑，可对每条数据进行转换、过滤、富化。

### 插件接口

```java
package org.dbsyncer.sdk.plugin;

public interface PluginContext extends BaseContext {
    ModelEnum getModelEnum();         // FULL / INCREMENT
    boolean isTerminated();           // 是否终止写入
    void setTerminated(boolean);      // 终止后续写入
    ConnectorInstance getTargetConnectorInstance();
    String getSourceTableName();
    String getTargetTableName();
    String getEvent();                // INSERT / UPDATE / DELETE
    List<Field> getTargetFields();
    int getBatchSize();
    boolean isForceUpdate();
    List<Map> getSourceList();        // 源端数据（只读）
    List<Map> getTargetList();        // 目标端数据（可修改）
    Plugin getPlugin();
    String getPluginExtInfo();        // 插件参数
    String getTraceId();
}
```

### 插件生命周期

插件在三个阶段被调用（`ProcessEnum`）：

1. **BEFORE** — 全量同步开始前（一次）
2. **CONVERT** — 每批数据读取后、写入前（多次）
3. **AFTER** — 全量同步结束后（一次）

### 开发步骤

#### 1. 创建 Maven 项目

```xml
<dependency>
    <groupId>org.ghi</groupId>
    <artifactId>dbsyncer-sdk</artifactId>
    <version>2.0.8</version>
    <scope>provided</scope>
</dependency>
```

#### 2. 实现 Plugin 接口

```java
package com.example.plugin;

import org.dbsyncer.sdk.plugin.PluginContext;
import org.dbsyncer.sdk.plugin.AbstractPluginContext;

public class MyConvertPlugin extends AbstractPluginContext {

    @Override
    public void convert(PluginContext context) {
        // 获取待写入数据
        List<Map> targetList = context.getTargetList();

        for (Map<String, Object> row : targetList) {
            // 示例：添加固定字段
            row.put("sync_time", System.currentTimeMillis());

            // 示例：字段值转换
            String name = (String) row.get("name");
            if (name != null) {
                row.put("name", name.toUpperCase());
            }
        }

        // 示例：根据条件终止写入
        if (targetList.isEmpty()) {
            context.setTerminated(true);
        }
    }
}
```

#### 3. 配置插件参数

插件可通过 `context.getPluginExtInfo()` 获取用户在 Web UI 中配置的 JSON 参数：

```java
String extInfo = context.getPluginExtInfo();
// extInfo = "{"threshold": 100, "mode": "strict"}"
// 使用 FastJSON2 解析
Map<String, Object> config = JSON.parseObject(extInfo);
```

#### 4. 打包和部署

```bash
mvn clean package
# 将生成的 JAR 放入 plugins/ 目录
cp target/my-plugin.jar /path/to/dbsyncer/plugins/
```

#### 5. 在 Web UI 中启用

1. 进入 `插件管理` 页面，上传 JAR 包
2. 在映射配置的 `插件` 选项卡中选择你的插件
3. 填写插件参数（JSON 格式）
4. 启动同步任务

### CONVERT 阶段详解

```java
@Override
public void convert(PluginContext context) {
    // 1. 获取源数据（只读）
    List<Map> sourceList = context.getSourceList();
    // 2. 获取目标数据（可修改）
    List<Map> targetList = context.getTargetList();
    // 3. 获取事件类型
    String event = context.getEvent();  // "INSERT", "UPDATE", "DELETE"
    // 4. 获取表名
    String sourceTable = context.getSourceTableName();
    String targetTable = context.getTargetTableName();
    // 5. 获取字段定义
    List<Field> targetFields = context.getTargetFields();
    // 6. 获取批次大小
    int batchSize = context.getBatchSize();
    // 7. 设置终止标记（跳过写入）
    context.setTerminated(true);
}
```

**关键点：**
- 修改 `targetList` 中的 Map 会直接影响写入目标库的数据
- `setTerminated(true)` 后，当前批次数据不会写入目标库
- 不要尝试修改 `sourceList`（只读引用）

---

## 二、自定义连接器

通过 Java SPI 机制注册自定义数据源/目标源。

### 开发步骤

#### 1. 实现 ConnectorService 接口

```java
package com.example.connector;

import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.model.ConnectorConfig;

public class MyCustomConnector implements ConnectorService<MyConnectorInstance, MyConfig> {

    @Override
    public String getConnectorType() {
        return "myCustom";
    }

    @Override
    public ConnectorInstance connect(MyConfig config, ConnectorServiceContext context) {
        // 建立连接，返回 ConnectorInstance
        MyConnectorInstance instance = new MyConnectorInstance();
        instance.setConfig(config);
        // ... 连接逻辑
        return instance;
    }

    @Override
    public void disconnect(MyConnectorInstance instance) {
        instance.close();
    }

    @Override
    public boolean isAlive(MyConnectorInstance instance) {
        // 健康检查
        return instance.getConnection() != null;
    }

    @Override
    public List<Table> getTable(MyConnectorInstance instance, ConnectorServiceContext context) {
        // 返回表列表
    }

    @Override
    public List<MetaInfo> getMetaInfo(MyConnectorInstance instance, ConnectorServiceContext context) {
        // 返回表字段信息
    }

    @Override
    public Result reader(MyConnectorInstance instance, ReaderContext context) {
        // 从数据源读取数据
    }

    @Override
    public Result writer(MyConnectorInstance instance, PluginContext context) {
        // 向目标源写入数据
    }

    @Override
    public Map<String, String> getSourceCommand(CommandConfig commandConfig) {
        // 返回数据源查询命令
    }

    @Override
    public Map<String, String> getTargetCommand(CommandConfig commandConfig) {
        // 返回目标源写入命令
    }

    @Override
    public Listener getListener(String listenerType) {
        // 返回增量监听器（如果支持）
    }

    @Override
    public SchemaResolver getSchemaResolver() {
        // 返回数据类型映射器
    }

    @Override
    public Class<MyConfig> getConfigClass() {
        return MyConfig.class;
    }
}
```

#### 2. 注册 SPI

创建 `META-INF/services/org.dbsyncer.sdk.spi.ConnectorService` 文件：

```
com.example.connector.MyCustomConnector
```

#### 3. 实现 ConnectorInstance

```java
public class MyConnectorInstance implements ConnectorInstance<MyConfig, MyConnection> {
    private MyConfig config;
    private MyConnection connection;

    @Override
    public String getServiceUrl() {
        return config.getUrl();
    }

    @Override
    public MyConfig getConfig() { return config; }

    @Override
    public void setConfig(MyConfig config) { this.config = config; }

    @Override
    public MyConnection getConnection() { return connection; }

    @Override
    public void close() {
        if (connection != null) connection.close();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();  // 浅拷贝
    }
}
```

#### 4. 打包为独立 JAR（推荐）

自定义连接器作为独立模块，打包后放入 `plugins/` 目录，系统通过 SPI 自动发现。

---

## 三、插件最佳实践

1. **幂等性**：CONVERT 阶段可能重试，确保转换逻辑幂等
2. **性能**：targetList 可能很大（默认 batchSize=1000），避免在逐行处理中做重量级操作
3. **错误处理**：抛出异常会终止当前批次同步，慎重处理
4. **日志**：使用 `context.getTraceId()` 关联全链路日志
5. **终止标记**：`setTerminated(true)` 仅在 BEFORE 或 CONVERT 阶段可调用
6. **参数解析**：`getPluginExtInfo()` 返回的 JSON 字符串需解析后使用，建议使用 FastJSON2
