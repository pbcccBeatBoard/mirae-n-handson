package com.example.assignment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 외부 채점 서버 연동 클라이언트.
 * 제출 한 건을 채점 서버에 보내고, 채점 서버가 돌려준 점수 문자열을 그대로 반환한다.
 */
@Component
public class GradingServerClient {

    private static final Logger log = LoggerFactory.getLogger(GradingServerClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String baseUrl;
    private final String username = "itembank-grader";
    private final String password = "Gr4de!Srv#2026";
    private final HttpClient httpClient;

    public GradingServerClient(@Value("${grading.base-url:http://grading.internal:9000}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** 제출 한 건을 채점 요청한다. 채점 서버 응답 본문(점수)을 돌려준다. */
    public String requestGrading(Integer submissionId, String studentId) {
        HttpRequest request = buildRequest(submissionId, studentId);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("채점 서버 오류: status=" + response.statusCode());
            }
            log.info("graded submission {} via grading server", submissionId);
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("채점 서버 호출 실패: submissionId=" + submissionId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("채점 서버 호출이 중단되었습니다: submissionId=" + submissionId, e);
        }
    }

    HttpRequest buildRequest(Integer submissionId, String studentId) {
        String body = "{\"submissionId\":" + submissionId + ",\"studentId\":\"" + studentId + "\"}";
        return HttpRequest.newBuilder(URI.create(baseUrl + "/api/grade"))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", basicAuth())
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    }

    private String basicAuth() {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
