package org.dbsyncer.connector.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchVersionInfo;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.Pair;
import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.elasticsearch.cdc.ESQuartzListener;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;
import org.dbsyncer.connector.elasticsearch.config.ESConfig;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;
import org.dbsyncer.connector.elasticsearch.enums.ESFieldTypeEnum;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;
import org.dbsyncer.connector.elasticsearch.schema.ElasticsearchSchemaResolver;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;
import org.dbsyncer.connector.elasticsearch.util.ESUtil;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;
import org.dbsyncer.connector.elasticsearch.validator.ESConfigValidator;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;
import org.dbsyncer.sdk.config.CommandConfig;
import org.dbsyncer.sdk.connector.AbstractConnector;
import org.dbsyncer.sdk.connector.ConfigValidator;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.connector.ConnectorServiceContext;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.enums.FilterEnum;
import org.dbsyncer.sdk.enums.ListenerTypeEnum;
import org.dbsyncer.sdk.enums.OperationEnum;
import org.dbsyncer.sdk.enums.TableTypeEnum;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.Filter;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.plugin.PluginContext;
import org.dbsyncer.sdk.plugin.ReaderContext;
import org.dbsyncer.sdk.schema.SchemaResolver;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ElasticsearchConnector extends AbstractConnector
        implements ConnectorService<ESConnectorInstance, ESConfig> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String _SOURCE_INDEX = "_source_index";
    private static final String _TARGET_INDEX = "_target_index";
    public static final String _TYPE = "_type";
    private final Map<String, FilterMapper> filters = new ConcurrentHashMap<>();
    private final ESConfigValidator configValidator = new ESConfigValidator();
    private final ElasticsearchSchemaResolver schemaResolver = new ElasticsearchSchemaResolver();

    private static final int V_7_0_0 = 7_00_00_00;

    public ElasticsearchConnector() {
        filters.putIfAbsent(FilterEnum.EQUAL.getName(),
                (builder, k, v) -> builder.must(QueryBuilders.match(m -> m.field(k).query(FieldValue.of(v)))));
        filters.putIfAbsent(FilterEnum.NOT_EQUAL.getName(),
                (builder, k, v) -> builder.mustNot(QueryBuilders.match(m -> m.field(k).query(FieldValue.of(v)))));
        filters.putIfAbsent(FilterEnum.GT.getName(),
                (builder, k, v) -> builder.filter(QueryBuilders.range(m -> m.untyped(ur -> ur.field(k).gt(JsonData.of(v))))));
        filters.putIfAbsent(FilterEnum.LT.getName(),
                (builder, k, v) -> builder.filter(QueryBuilders.range(m -> m.untyped(ur -> ur.field(k).lt(JsonData.of(v))))));
        filters.putIfAbsent(FilterEnum.GT_AND_EQUAL.getName(),
                (builder, k, v) -> builder.filter(QueryBuilders.range(m -> m.untyped(ur -> ur.field(k).gte(JsonData.of(v))))));
        filters.putIfAbsent(FilterEnum.LT_AND_EQUAL.getName(),
                (builder, k, v) -> builder.filter(QueryBuilders.range(m -> m.untyped(ur -> ur.field(k).lte(JsonData.of(v))))));
        filters.putIfAbsent(FilterEnum.LIKE.getName(),
                (builder, k, v) -> builder.filter(QueryBuilders.wildcard(m -> m.field(k).wildcard(v))));
    }

    private static int parseVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major * 10_000_000 + minor * 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isVersionBefore7(String ver) { return parseVersion(ver) < V_7_0_0; }

    @Override public String getConnectorType() { return "Elasticsearch"; }
    @Override public TableTypeEnum getExtendedTableType() { return TableTypeEnum.SEMI; }
    @Override public Class<ESConfig> getConfigClass() { return ESConfig.class; }
    @Override public ConnectorInstance connect(ESConfig config, ConnectorServiceContext context) { return new ESConnectorInstance(config); }
    @Override public ConfigValidator getConfigValidator() { return configValidator; }
    @Override public void disconnect(ESConnectorInstance connectorInstance) { connectorInstance.close(); }
    @Override public SchemaResolver getSchemaResolver() { return schemaResolver; }

    @Override
    public boolean isAlive(ESConnectorInstance connectorInstance) {
        try {
            return connectorInstance.getConnection().ping().value();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> getDatabases(ESConnectorInstance connectorInstance) {
        try {
            String clusterName = connectorInstance.getConnection().info().clusterName();
            return Collections.singletonList(clusterName);
        } catch (Exception e) {
            logger.error("获取ES集群名称失败: {}", e.getMessage());
            throw new ElasticsearchException("获取ES集群名称失败: " + e.getMessage());
        }
    }

    @Override
    public List<Table> getTable(ESConnectorInstance connectorInstance, ConnectorServiceContext context) {
        try {
            GetAliasResponse aliasResponse = connectorInstance.getConnection().indices().getAlias();
            Map<String, IndexAliases> aliases = aliasResponse.result();
            if (!CollectionUtils.isEmpty(aliases)) {
                return aliases.keySet().stream()
                        .filter(index -> !StringUtil.startsWith(index, "."))
                        .map(name -> {
                            Table table = new Table();
                            table.setName(name);
                            table.setType(TableTypeEnum.TABLE.getCode());
                            return table;
                        }).collect(Collectors.toList());
            }
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ElasticsearchException(e.getMessage());
        }
    }

    @Override
    public List<MetaInfo> getMetaInfo(ESConnectorInstance connectorInstance, ConnectorServiceContext context) {
        List<MetaInfo> metaInfos = new ArrayList<>();
        try {
            String ver = connectorInstance.getVersion();
            boolean before7 = isVersionBefore7(ver);

            for (Table table : context.getTablePatterns()) {
                if (TableTypeEnum.getTableType(table.getType()) == getExtendedTableType()) {
                    getExtendedMetaInfo(connectorInstance, metaInfos, table, ver);
                    continue;
                }
                String index = table.getName();
                GetMappingResponse mappingResponse = connectorInstance.getConnection()
                        .indices().getMapping(m -> m.index(index));
                Map<String, co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord> mappings =
                        mappingResponse.result();
                List<Field> fields = new ArrayList<>();
                co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord record = mappings.get(index);

                if (record != null && record.mappings() != null) {
                    if (before7) {
                        Map<String, Object> sourceMap = record.mappings().properties()
                                .entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey,
                                        e -> e.getValue()._kind() == null ? new HashMap<>() : new HashMap<>()));
                        if (CollectionUtils.isEmpty(sourceMap)) {
                            throw new ElasticsearchException("未获取到索引配置");
                        }
                        Iterator<String> iterator = sourceMap.keySet().iterator();
                        String indexType = null;
                        if (iterator.hasNext()) {
                            indexType = iterator.next();
                            Object typeMap = sourceMap.get(indexType);
                            if (typeMap instanceof Map) {
                                parseProperties(fields, (Map) typeMap);
                            }
                        }
                        if (StringUtil.isBlank(indexType)) {
                            throw new ElasticsearchException("索引type为空");
                        }
                        metaInfos.add(buildMetaInfo(table.getType(), index, fields, indexType));
                    } else {
                        parsePropertiesFromMapping(fields, record.mappings().properties());
                        metaInfos.add(buildMetaInfo(table.getType(), index, fields, null));
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ElasticsearchException(e.getMessage());
        }
        return metaInfos;
    }

    private void getExtendedMetaInfo(ESConnectorInstance connectorInstance, List<MetaInfo> metaInfos, Table table, String ver) {
        MetaInfo metaInfo = new MetaInfo();
        metaInfo.setTable(table.getName());
        metaInfo.setTableType(table.getType());
        metaInfo.setColumn(table.getColumn());
        Properties extInfo = metaInfo.getExtInfo();
        extInfo.putAll(table.getExtInfo());
        if (isVersionBefore7(ver)) {
            extInfo.put(_TYPE, null);
        } else {
            extInfo.setProperty(_TYPE, extInfo.getProperty(_TYPE, "_doc"));
        }
        metaInfos.add(metaInfo);
    }

    private MetaInfo buildMetaInfo(String tableType, String index, List<Field> fields, String indexType) {
        MetaInfo metaInfo = new MetaInfo();
        metaInfo.setTable(index);
        metaInfo.setTableType(tableType);
        metaInfo.setColumn(fields);
        if (StringUtil.isNotBlank(indexType)) {
            metaInfo.getExtInfo().put(_TYPE, indexType);
        }
        return metaInfo;
    }

    @Override
    public long getCount(ESConnectorInstance connectorInstance, Map<String, String> command) {
        try {
            SearchResponse<Void> response = connectorInstance.getConnection()
                    .search(s -> s.index(command.get(_SOURCE_INDEX))
                            .trackTotalHits(th -> th.enabled(true))
                            .size(0)
                            .query(buildQuery(command)), Void.class);
            return response.hits().total().value();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ElasticsearchException(e.getMessage());
        }
    }

    @Override
    public Result reader(ESConnectorInstance connectorInstance, ReaderContext context) {
        List<String> primaryKeys = PrimaryKeyUtil.findTablePrimaryKeys(context.getSourceTable());
        int pageSize = Math.min(context.getPageSize(), 10000);

        try {
            SearchRequest.Builder builder = new SearchRequest.Builder()
                    .index(context.getCommand().get(_SOURCE_INDEX))
                    .query(buildQuery(context.getCommand()))
                    .size(pageSize);

            if (!CollectionUtils.isEmpty(context.getCursors())) {
                builder.searchAfter(Arrays.stream(context.getCursors())
                        .map(FieldValue::of).collect(Collectors.toList()));
            } else {
                builder.from((context.getPageIndex() - 1) * pageSize);
            }

            for (String pk : primaryKeys) {
                builder.sort(s -> s.field(f -> f.field(pk).order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
            }

            SearchResponse<Map> response = connectorInstance.getConnection()
                    .search(builder.build(), Map.class);

            List<Map<String, Object>> list = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null) list.add(hit.source());
            }
            if (response.timedOut()) {
                throw new ElasticsearchException("search timeout:" + response.took() + "ms");
            }
            return new Result(list);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new ElasticsearchException(e.getMessage());
        }
    }

    @Override
    public Result writer(ESConnectorInstance connectorInstance, PluginContext context) {
        List<Map> data = context.getTargetList();
        if (CollectionUtils.isEmpty(data)) {
            throw new ElasticsearchException("writer data can not be empty.");
        }

        Result result = new Result();
        final List<Field> pkFields = PrimaryKeyUtil.findExistPrimaryKeyFields(context.getTargetFields());
        try {
            final String pk = pkFields.get(0).getName();
            final String indexName = context.getCommand().get(_TARGET_INDEX);
            String event = context.getEvent();
            List<BulkOperation> operations = new ArrayList<>();

            for (Map row : data) {
                String id = String.valueOf(row.get(pk));
                if (isUpdate(event)) {
                    operations.add(BulkOperation.of(op -> op
                            .update(upd -> upd.index(indexName).id(id)
                                    .action(a -> a.doc(JsonData.of(row))))));
                } else if (isInsert(event)) {
                    operations.add(BulkOperation.of(op -> op
                            .index(idx -> idx.index(indexName).id(id)
                                    .document(JsonData.of(row)))));
                } else if (isDelete(event)) {
                    operations.add(BulkOperation.of(op -> op
                            .delete(del -> del.index(indexName).id(id))));
                }
            }

            BulkResponse response = connectorInstance.getConnection()
                    .bulk(BulkRequest.of(b -> b.operations(operations)));

            if (response.errors()) {
                for (int i = 0; i < response.items().size(); i++) {
                    BulkResponseItem item = response.items().get(i);
                    if (item.error() != null) {
                        result.getFailData().add(data.get(i));
                        result.getError().append("\n[").append(i).append("]: index [")
                                .append(item.index()).append("], id [").append(item.id())
                                .append("], message [").append(item.error().reason()).append("]");
                    } else {
                        result.getSuccessData().add(data.get(i));
                    }
                }
            } else {
                result.getSuccessData().addAll(data);
            }
        } catch (Exception e) {
            result.addFailData(data);
            result.getError().append(e.getMessage()).append(System.lineSeparator());
            logger.error(e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, String> getSourceCommand(CommandConfig commandConfig) {
        Map<String, String> command = new HashMap<>();
        Table table = commandConfig.getTable();
        command.put(_SOURCE_INDEX, table.getName());
        List<Field> column = table.getColumn();
        if (!CollectionUtils.isEmpty(column)) {
            command.put(ConnectorConstant.OPERTION_QUERY,
                    StringUtil.join(column.stream().map(Field::getName).collect(Collectors.toList()), ","));
        }
        List<Filter> filter = commandConfig.getFilter();
        if (!CollectionUtils.isEmpty(filter)) {
            command.put(ConnectorConstant.OPERTION_QUERY_FILTER, JsonUtil.objToJson(filter));
        }
        return command;
    }

    @Override
    public Map<String, String> getTargetCommand(CommandConfig commandConfig) {
        Table table = commandConfig.getTable();
        PrimaryKeyUtil.findTablePrimaryKeys(table);
        Map<String, String> command = new HashMap<>();
        command.put(_TARGET_INDEX, table.getName());
        command.put(_TYPE, String.valueOf(table.getExtInfo().get(_TYPE)));
        return command;
    }

    @Override
    public Listener getListener(String listenerType) {
        if (ListenerTypeEnum.isTiming(listenerType)) {
            return new ESQuartzListener();
        }
        return null;
    }

    private void parseProperties(List<Field> fields, Map<String, Object> sourceMap) {
        if (CollectionUtils.isEmpty(sourceMap)) {
            throw new ElasticsearchException("未获取到索引字段.");
        }
        Map<String, Object> properties = (Map<String, Object>) sourceMap.get(ESUtil.PROPERTIES);
        if (CollectionUtils.isEmpty(properties)) {
            throw new ElasticsearchException("查询字段不能为空.");
        }
        properties.forEach((fieldName, c) -> {
            Map fieldDesc = (Map) c;
            String columnType = (String) fieldDesc.get("type");
            if (columnType == null) {
                columnType = ESFieldTypeEnum.OBJECT.getCode();
            }
            if (StringUtil.equals(ESFieldTypeEnum.DATE.getCode(), columnType)) {
                Object format = fieldDesc.get("format");
                if (format != null) {
                    Field dateField = new Field(fieldName, columnType, ESFieldTypeEnum.KEYWORD.getType());
                    dateField.getExtInfo().put("format", format);
                    fields.add(dateField);
                    return;
                }
            }
            fields.add(new Field(fieldName, columnType, ESFieldTypeEnum.getType(columnType)));
        });
    }

    private void parsePropertiesFromMapping(List<Field> fields,
            Map<String, co.elastic.clients.elasticsearch._types.mapping.Property> properties) {
        if (CollectionUtils.isEmpty(properties)) {
            throw new ElasticsearchException("查询字段不能为空.");
        }
        for (Map.Entry<String, co.elastic.clients.elasticsearch._types.mapping.Property> entry : properties.entrySet()) {
            String fieldName = entry.getKey();
            co.elastic.clients.elasticsearch._types.mapping.Property prop = entry.getValue();
            String columnType = prop.isText() ? "text" :
                    prop.isKeyword() ? "keyword" :
                    prop.isLong() ? "long" :
                    prop.isInteger() ? "integer" :
                    prop.isDouble() ? "double" :
                    prop.isFloat() ? "float" :
                    prop.isBoolean() ? "boolean" :
                    prop.isDate() ? "date" :
                    prop.isBinary() ? "binary" :
                    prop.isObject() ? "object" :
                    "keyword";
            int dbType = ESFieldTypeEnum.getType(columnType);
            fields.add(new Field(fieldName, columnType, dbType));
        }
    }

    private Query buildQuery(Map<String, String> command) {
        String filterJson = command.get(ConnectorConstant.OPERTION_QUERY_FILTER);
        List<Filter> filtersList = null;
        if (!StringUtil.isBlank(filterJson)) {
            filtersList = JsonUtil.jsonToArray(filterJson, Filter.class);
        }
        if (CollectionUtils.isEmpty(filtersList)) {
            return QueryBuilders.matchAll().build()._toQuery();
        }

        List<Filter> and = filtersList.stream()
                .filter(f -> OperationEnum.isAnd(f.getOperation())).collect(Collectors.toList());
        List<Filter> or = filtersList.stream()
                .filter(f -> OperationEnum.isOr(f.getOperation())).collect(Collectors.toList());

        BoolQuery.Builder q = new BoolQuery.Builder();
        if (!CollectionUtils.isEmpty(and) && !CollectionUtils.isEmpty(or)) {
            BoolQuery.Builder andQuery = new BoolQuery.Builder();
            and.forEach(f -> addFilter(andQuery, f));
            q.should(andQuery.build()._toQuery());
            genShouldQuery(q, or);
            return q.build()._toQuery();
        }
        if (!CollectionUtils.isEmpty(or)) {
            genShouldQuery(q, or);
            return q.build()._toQuery();
        }
        and.forEach(f -> addFilter(q, f));
        return q.build()._toQuery();
    }

    private void genShouldQuery(BoolQuery.Builder q, List<Filter> or) {
        for (Filter f : or) {
            BoolQuery.Builder orQuery = new BoolQuery.Builder();
            addFilter(orQuery, f);
            q.should(orQuery.build()._toQuery());
        }
    }

    private void addFilter(BoolQuery.Builder builder, Filter f) {
        if (filters.containsKey(f.getFilter())) {
            filters.get(f.getFilter()).apply(builder, f.getName(), f.getValue());
        }
    }

    private interface FilterMapper {
        void apply(BoolQuery.Builder builder, String key, String value);
    }
}
