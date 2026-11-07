package com.dh.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

/** gateway#369 — 대표 호스트로 보내되 경로·쿼리를 유지하는지. */
class CanonicalHostRedirectGatewayFilterFactoryTest {

    private final CanonicalHostRedirectGatewayFilterFactory factory = new CanonicalHostRedirectGatewayFilterFactory();

    private GatewayFilter filter(String status, String host) {
        CanonicalHostRedirectGatewayFilterFactory.Config config = new CanonicalHostRedirectGatewayFilterFactory.Config();
        config.setStatus(status);
        config.setHost(host);
        return factory.apply(config);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "https://home.posselect.com/                   | https://www.posselect.com/",
            "https://home.posselect.com                    | https://www.posselect.com/",
            "https://home.posselect.com/faq                | https://www.posselect.com/faq",
            "https://home.posselect.com/notices/3?page=2&q=a | https://www.posselect.com/notices/3?page=2&q=a",
            // 인코딩된 경로·쿼리는 그대로 옮긴다(이중 인코딩 금지)
            "https://home.posselect.com/search?q=%EC%9A%B0%EC%9C%A0 | https://www.posselect.com/search?q=%EC%9A%B0%EC%9C%A0",
            "https://home.posselect.com/a%20b              | https://www.posselect.com/a%20b",
            // 경로에 다른 호스트를 넣어도 대상 호스트는 설정값으로 고정
            "https://home.posselect.com//evil.example/x    | https://www.posselect.com//evil.example/x",
    })
    void 경로와_쿼리를_유지해_대표_호스트로_보낸다(String requestUrl, String expectedLocation) {
        // get(String) 은 URL 을 템플릿으로 보고 다시 인코딩·정규화한다 — 실제 요청처럼 URI 를 그대로 넘긴다.
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.GET, URI.create(requestUrl)).build());

        filter("301", "www.posselect.com").filter(exchange, e -> Mono.error(new AssertionError("체인으로 넘기면 안 된다")))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo(expectedLocation);
    }

    @Test
    void 리다이렉트가_아닌_상태코드는_설정_단계에서_거부한다() {
        assertThatThrownBy(() -> filter("200", "www.posselect.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 호스트에_스킴이나_경로를_넣으면_거부한다() {
        assertThatThrownBy(() -> filter("301", "https://www.posselect.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> filter("301", "www.posselect.com/x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> filter("301", " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
