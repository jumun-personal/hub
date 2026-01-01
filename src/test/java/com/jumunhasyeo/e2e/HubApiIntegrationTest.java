package com.jumunhasyeo.e2e;

import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.presentation.dto.request.CreateHubReq;
import com.jumunhasyeo.testsupport.AbstractHttpIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class HubApiIntegrationTest extends AbstractHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Override
    protected void beforeEachAfterTruncate() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("허브 생성부터 조회까지 전체 플로우")
    void createAndGetHub_EndToEndFlow() {
        // given
        CreateHubReq request = new CreateHubReq(null,"이름", "서울시 송파구 허브", 12.6, 12.6, HubType.CENTER);

        // when: 허브 생성
        String hubId = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/hubs")
                .then()
                .statusCode(201)
                .extract().path("data.id");

        // then: 생성된 허브 조회
        given()
                .when()
                .get("/internal/api/v1/hubs/{hubId}", hubId)
                .then()
                .statusCode(200)
                .body("data.id", equalTo(hubId));
    }

    @Test
    @DisplayName("허브 생성시 중복된 이름일 경우 예외 반환")
    void createDuplicateName_shouldThrowException() {
        // given
        CreateHubReq request = new CreateHubReq(null,"이름", "서울시 송파구 허브", 36.3505, 127.3845, HubType.CENTER);

        // when: 허브 2개 생성
        String hubId = given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/hubs")
                .then()
                .statusCode(201)
                .extract().path("data.id");

        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/hubs")
                .then()
                .statusCode(400)
                .body("code", equalTo(ErrorCode.ALREADY_EXISTS.name()))
                .body("message", containsString("이미 존재하는 허브 이름입니다."));

    }
}
