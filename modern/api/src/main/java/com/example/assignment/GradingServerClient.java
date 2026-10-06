package com.example.assignment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final Pattern STUDENT_ID = Pattern.compile("STU-\\d+");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient httpClient;

    // 인증 정보는 기본값 없이 환경 설정으로만 주입한다(코드·저장소에 두지 않는다).
    // 운영 · bootRun 에서는 환경변수 GRADING_USERNAME / GRADING_PASSWORD 가 필요하다(없으면 기동 실패).
    public GradingServerClient(
        @Value("${grading.base-url:http://grading.internal:9000}") String baseUrl,
        @Value("${grading.username}") String username,
        @Value("${grading.password}") String password) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
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
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId 가 필요합니다");
        }
        if (studentId == null || !STUDENT_ID.matcher(studentId).matches()) {
            throw new IllegalArgumentException("studentId 형식이 올바르지 않습니다");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", submissionId);
        payload.put("studentId", studentId);
        String body;
        try {
            body = MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("채점 요청 본문 직렬화 실패", e);
        }
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
