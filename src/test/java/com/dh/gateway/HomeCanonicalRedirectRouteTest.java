package com.dh.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

/**
 * home.posselect.com → www.posselect.com 301(gateway#369) 라우트의 위치를 고정한다.
 *
 * <p>API 라우트보다 위로 올라가면 home 출처에서 열린 화면의 로그인 상태 조회·찜 쓰기가 리다이렉트로 깨지고
 * (fetch 는 301 을 따라가 GET 으로 바꾼다), store-front 보다 아래로 내려가면 리다이렉트가 아예 안 걸린다 —
 * 둘 다 다른 테스트를 깨지 않고 조용히 일어난다.
 */
@SpringBootTest
class HomeCanonicalRedirectRouteTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void homeCanonicalRedirectSitsBelowHomeApiRoutesAndAboveTheFrontRoute() {
        List<String> ids = routeLocator.getRoutes().map(Route::getId).collectList().block();

        assertThat(ids).contains("auth-api-home", "product-api-wishlist-home", "store-front-home-canonical", "store-front");
        int canonical = ids.indexOf("store-front-home-canonical");
        assertThat(ids.indexOf("auth-api-home")).as("로그인 상태 조회가 먼저 잡혀야 한다").isLessThan(canonical);
        assertThat(ids.indexOf("product-api-wishlist-home")).as("찜 API 가 먼저 잡혀야 한다").isLessThan(canonical);
        assertThat(canonical).as("store-front 보다 위여야 리다이렉트가 걸린다").isLessThan(ids.indexOf("store-front"));
    }
}
