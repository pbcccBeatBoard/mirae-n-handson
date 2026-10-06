package com.example.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GradingServerClientTest {

    private final GradingServerClient client = new GradingServerClient("http://grading.test:9000");

    @Test
    @DisplayName("buildRequest: 채점 서버 /api/grade 로 POST, JSON 본문과 Basic 인증 헤더를 붙인다")
    void buildsGradingRequest() {
        HttpRequest request = client.buildRequest(42, "STU-1001");

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).hasToString("http://grading.test:9000/api/grade");
        assertThat(request.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(request.headers().firstValue("Authorization")).hasValueSatisfying(
            value -> assertThat(value).startsWith("Basic "));
    }
}
