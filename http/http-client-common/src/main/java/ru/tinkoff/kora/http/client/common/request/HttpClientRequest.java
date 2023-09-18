package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.body.HttpOutBody;

import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public interface HttpClientRequest {
    String method();

    String uriTemplate();

    List<QueryParam> queryParams();

    List<TemplateParam> templateParams();

    HttpHeaders headers();

    HttpOutBody body();

    String resolvedUri();

    String authority();

    String operation();

    @Nullable
    Duration requestTimeout();

    record TemplateParam(String name, String value) {
        public TemplateParam {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }

    record QueryParam(String name, @Nullable String value) {
        public QueryParam {
            Objects.requireNonNull(name);
        }
    }

    default HttpClientRequestBuilder toBuilder() {
        return new HttpClientRequestBuilder(this);
    }

    static HttpClientRequestBuilder get(String path) {
        return new HttpClientRequestBuilder(HttpMethod.GET, path);
    }

    static HttpClientRequestBuilder head(String path) {
        return new HttpClientRequestBuilder(HttpMethod.HEAD, path);
    }

    static HttpClientRequestBuilder post(String path) {
        return new HttpClientRequestBuilder(HttpMethod.POST, path);
    }

    static HttpClientRequestBuilder put(String path) {
        return new HttpClientRequestBuilder(HttpMethod.PUT, path);
    }

    static HttpClientRequestBuilder delete(String path) {
        return new HttpClientRequestBuilder(HttpMethod.DELETE, path);
    }

    static HttpClientRequestBuilder connect(String path) {
        return new HttpClientRequestBuilder(HttpMethod.CONNECT, path);
    }

    static HttpClientRequestBuilder options(String path) {
        return new HttpClientRequestBuilder(HttpMethod.OPTIONS, path);
    }

    static HttpClientRequestBuilder trace(String path) {
        return new HttpClientRequestBuilder(HttpMethod.TRACE, path);
    }

    static HttpClientRequestBuilder patch(String path) {
        return new HttpClientRequestBuilder(HttpMethod.PATCH, path);
    }

    static HttpClientRequestBuilder of(String method, String path) {
        return new HttpClientRequestBuilder(method, path);
    }


    record Default(
        String method,
        String uriTemplate,
        List<QueryParam> queryParams,
        List<TemplateParam> templateParams,
        HttpHeaders headers,
        HttpOutBody body,
        Duration requestTimeout,
        String resolvedUri,
        String authority,
        String operation
    ) implements HttpClientRequest {
    }
}
