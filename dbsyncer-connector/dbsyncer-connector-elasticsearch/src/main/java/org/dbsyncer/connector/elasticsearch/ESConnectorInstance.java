package org.dbsyncer.connector.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.dbsyncer.connector.elasticsearch.config.ESConfig;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;
import org.dbsyncer.connector.elasticsearch.util.ESUtil;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;
import org.dbsyncer.sdk.connector.ConnectorInstance;


public final class ESConnectorInstance implements ConnectorInstance<ESConfig, ElasticsearchClient> {
    private ESConfig config;
    private ElasticsearchClient client;
    private String version;

    public ESConnectorInstance(ESConfig config) {
        this.config = config;
        this.client = ESUtil.getClient(config);
        try {
            this.version = client.info().version().number();
        } catch (Exception e) {
            throw new ElasticsearchException(
                    String.format("获取ES版本信息异常 %s, %s", config.getUrl(), e.getMessage()));
        }
    }

    @Override
    public String getServiceUrl() {
        return config.getUrl();
    }

    @Override
    public ESConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(ESConfig config) {
        this.config = config;
    }

    @Override
    public ElasticsearchClient getConnection() {
        return client;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public void close() {
        ESUtil.close(client);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
