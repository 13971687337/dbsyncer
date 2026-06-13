package org.dbsyncer.connector.elasticsearch.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.elasticsearch.config.ESConfig;
import org.dbsyncer.connector.elasticsearch.ElasticsearchException;

import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public abstract class ESUtil {

    public static final String PROPERTIES = "properties";

    private ESUtil() {}

    public static ElasticsearchClient getClient(ESConfig config) {
        String[] ipAddress = StringUtil.split(config.getUrl(), StringUtil.COMMA);
        HttpHost[] hosts = Arrays.stream(ipAddress)
                .map(HttpHost::create)
                .filter(Objects::nonNull)
                .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(hosts);
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(config.getUsername(), config.getPassword()));

        try {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(new TrustAllStrategy()).build();
            SSLIOSessionStrategy sessionStrategy = new SSLIOSessionStrategy(
                    sslContext, NoopHostnameVerifier.INSTANCE);
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                            .setSSLStrategy(sessionStrategy));

            RestClient restClient = builder.build();
            RestClientTransport transport = new RestClientTransport(
                    restClient, new JacksonJsonpMapper());
            ElasticsearchClient client = new ElasticsearchClient(transport);
            client.ping();
            return client;
        } catch (Exception e) {
            throw new ElasticsearchException(
                    String.format("Failed to connect to ElasticSearch on %s, %s",
                            config.getUrl(), e.getMessage()));
        }
    }

    public static void close(ElasticsearchClient client) {
        if (client != null && client._transport() != null) {
            try {
                client._transport().close();
            } catch (IOException e) {
                throw new ElasticsearchException(e.getMessage());
            }
        }
    }
}
