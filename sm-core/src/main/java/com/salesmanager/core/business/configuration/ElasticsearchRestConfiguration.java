package com.salesmanager.core.business.configuration;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientProperties;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@AutoConfigureBefore(ElasticsearchRestClientAutoConfiguration.class)
public class ElasticsearchRestConfiguration {

    @Configuration
    static class ElasticsearchRestProperties {
        @Value("${elasticsearch.server.host}")
        private List<String> hosts;

        @Value("${elasticsearch.server.protocole}")
        private String protocol;

        @Value("${elasticsearch.server.port}")
        private int port;

        @Value("${elasticsearch.security.enabled}")
        private Boolean securityEnabled;

        @Value("${elasticsearch.security.user}")
        private String user;

        @Value("${elasticsearch.security.password}")
        private String password;

        public List<String> getHosts() {
            return hosts;
        }

        public void setHosts(List<String> hosts) {
            this.hosts = hosts;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public Boolean getSecurityEnabled() {
            return securityEnabled;
        }

        public void setSecurityEnabled(Boolean securityEnabled) {
            this.securityEnabled = securityEnabled;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        private List<String> getUris() {
            return getHosts().stream().map(host -> host + ":" + getPort()).collect(Collectors.toList());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(RestClientBuilder.class)
    static class RestClientBuilderConfiguration {

        @Bean
        RestClientBuilderCustomizer defaultRestClientBuilderCustomizer(ElasticsearchRestClientProperties properties) {
            return new DefaultRestClientBuilderCustomizer(properties);
        }

        @Bean
        RestClientBuilder elasticsearchRestClientBuilder(ElasticsearchRestClientProperties properties, ElasticsearchRestProperties customProperties,
                                                         ObjectProvider<RestClientBuilderCustomizer> builderCustomizers) {
            HttpHost[] hosts = customProperties.getUris().stream().map(this::createHttpHost).toArray(HttpHost[]::new);
            if (Boolean.TRUE.equals(customProperties.getSecurityEnabled())) {
                properties.setUsername(customProperties.getUser());
                properties.setPassword(customProperties.getPassword());
            }
            RestClientBuilder builder = RestClient.builder(hosts);
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                builderCustomizers.orderedStream().forEach(customizer -> customizer.customize(httpClientBuilder));
                return httpClientBuilder;
            });
            builder.setRequestConfigCallback(requestConfigBuilder -> {
                builderCustomizers.orderedStream().forEach(customizer -> customizer.customize(requestConfigBuilder));
                return requestConfigBuilder;
            });
            builderCustomizers.orderedStream().forEach(customizer -> customizer.customize(builder));
            return builder;
        }

        private HttpHost createHttpHost(String uri) {
            try {
                return createHttpHost(URI.create(uri));
            }
            catch (IllegalArgumentException ex) {
                return HttpHost.create(uri);
            }
        }

        private HttpHost createHttpHost(URI uri) {
            if (!StringUtils.hasLength(uri.getUserInfo())) {
                return HttpHost.create(uri.toString());
            }
            try {
                return HttpHost.create(new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(),
                        uri.getQuery(), uri.getFragment()).toString());
            }
            catch (URISyntaxException ex) {
                throw new IllegalStateException(ex);
            }
        }

    }


    static class DefaultRestClientBuilderCustomizer implements RestClientBuilderCustomizer {

        private static final PropertyMapper map = PropertyMapper.get();

        private final ElasticsearchRestClientProperties properties;

        DefaultRestClientBuilderCustomizer(ElasticsearchRestClientProperties properties) {
            this.properties = properties;
        }

        @Override
        public void customize(RestClientBuilder builder) {
        }

        @Override
        public void customize(HttpAsyncClientBuilder builder) {
            builder.setDefaultCredentialsProvider(new PropertiesCredentialsProvider(this.properties));
        }

        @Override
        public void customize(RequestConfig.Builder builder) {
            map.from(this.properties::getConnectionTimeout).whenNonNull().asInt(Duration::toMillis)
                    .to(builder::setConnectTimeout);
            map.from(this.properties::getReadTimeout).whenNonNull().asInt(Duration::toMillis)
                    .to(builder::setSocketTimeout);
        }

    }

    private static class PropertiesCredentialsProvider extends BasicCredentialsProvider {

        PropertiesCredentialsProvider(ElasticsearchRestClientProperties properties) {
            if (StringUtils.hasText(properties.getUsername())) {
                Credentials credentials = new UsernamePasswordCredentials(properties.getUsername(),
                        properties.getPassword());
                setCredentials(AuthScope.ANY, credentials);
            }
            properties.getUris().stream().map(this::toUri).filter(this::hasUserInfo)
                    .forEach(this::addUserInfoCredentials);
        }

        private URI toUri(String uri) {
            try {
                return URI.create(uri);
            }
            catch (IllegalArgumentException ex) {
                return null;
            }
        }

        private boolean hasUserInfo(URI uri) {
            return uri != null && StringUtils.hasLength(uri.getUserInfo());
        }

        private void addUserInfoCredentials(URI uri) {
            AuthScope authScope = new AuthScope(uri.getHost(), uri.getPort());
            Credentials credentials = createUserInfoCredentials(uri.getUserInfo());
            setCredentials(authScope, credentials);
        }

        private Credentials createUserInfoCredentials(String userInfo) {
            int delimiter = userInfo.indexOf(":");
            if (delimiter == -1) {
                return new UsernamePasswordCredentials(userInfo, null);
            }
            String username = userInfo.substring(0, delimiter);
            String password = userInfo.substring(delimiter + 1);
            return new UsernamePasswordCredentials(username, password);
        }

    }
}
