package com.dh.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

import java.net.URI;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.GatewayToStringStyler;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Mono;

/**
 * 요청을 대표 호스트로 돌려보내되 <b>경로와 쿼리를 그대로 유지</b>하는 리다이렉트 (gateway#369).
 *
 * <p>내장 {@code RedirectTo} 는 설정한 URL 로만 보낸다 — 쿼리는 {@code includeRequestParams} 로 붙일 수 있지만
 * 경로는 버린다(SCG 5.0.3 바이트코드로 확인). 그래서 {@code posselect.com/faq} 가 {@code www.posselect.com/}
 * 으로 떨어져 외부 링크의 검색 신호가 메인으로만 모였다.
 *
 * <p>사용: {@code CanonicalHostRedirect=301, www.posselect.com}. 대상 호스트는 설정값으로 고정되고 요청에서
 * 가져오는 것은 경로·쿼리뿐이라, 경로에 {@code //evil.com} 을 넣어도 다른 호스트로 가지 않는다.
 * 경로·쿼리는 원래 인코딩 그대로({@code getRawPath}/{@code getRawQuery}) 옮겨 이중 인코딩을 피한다.
 */
@Component
public class CanonicalHostRedirectGatewayFilterFactory
        extends AbstractGatewayFilterFactory<CanonicalHostRedirectGatewayFilterFactory.Config> {

    public CanonicalHostRedirectGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("status", "host");
    }

    @Override
    public GatewayFilter apply(Config config) {
        HttpStatusHolder status = HttpStatusHolder.parse(config.getStatus());
        Assert.isTrue(status.is3xxRedirection(), "status must be a 3xx code, but was " + config.getStatus());
        String host = config.getHost();
        Assert.isTrue(StringUtils.hasText(host) && !host.contains("/") && !host.contains(":"),
                "host must be a bare host name (no scheme, port or path), but was " + host);

        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,
                    org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
                if (exchange.getResponse().isCommitted()) {
                    return Mono.empty();
                }
                setResponseStatus(exchange, status);
                exchange.getResponse().getHeaders().set(HttpHeaders.LOCATION,
                        location(host, exchange.getRequest().getURI()));
                return exchange.getResponse().setComplete();
            }

            @Override
            public String toString() {
                return GatewayToStringStyler.filterToStringCreator(CanonicalHostRedirectGatewayFilterFactory.this)
                        .append("status", config.getStatus())
                        .append("host", host)
                        .toString();
            }
        };
    }

    static String location(String host, URI request) {
        String path = request.getRawPath();
        String query = request.getRawQuery();
        return "https://" + host
                + (StringUtils.hasLength(path) ? path : "/")
                + (StringUtils.hasLength(query) ? "?" + query : "");
    }

    public static class Config {
        private String status;
        private String host;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }
    }
}
