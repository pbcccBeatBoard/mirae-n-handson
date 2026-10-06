package com.example.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Flow;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class GradingServerClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GradingServerClient client =
        new GradingServerClient("http://grading.test:9000", "grader", "s3cret-pw");

    @Test
    @DisplayName("buildRequest: 채점 서버 /api/grade 로 POST, JSON 본문과 Basic 인증 헤더를 붙인다")
    void buildsGradingRequest() {
        HttpRequest request = client.buildRequest(42, "STU-1001");

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).hasToString("http://grading.test:9000/api/grade");
        assertThat(request.headers().firstValue("Content-Type")).hasValue("application/json");
        String expected = "Basic " + Base64.getEncoder()
            .encodeToString("grader:s3cret-pw".getBytes(StandardCharsets.UTF_8));
        assertThat(request.headers().firstValue("Authorization")).hasValue(expected);
    }

    @Test
    @DisplayName("buildRequest: 본문은 submissionId 숫자와 studentId 문자열을 가진 JSON 이다")
    void bodyIsJson() throws Exception {
        JsonNode body = MAPPER.readTree(bodyOf(client.buildRequest(42, "STU-1001")));

        assertThat(body.get("submissionId").isInt()).isTrue();
        assertThat(body.get("submissionId").asInt()).isEqualTo(42);
        assertThat(body.get("studentId").asText()).isEqualTo("STU-1001");
        assertThat(body.size()).isEqualTo(2);
    }

    @ParameterizedTest(name = "거부: [{0}]")
    @ValueSource(strings = {
        "STU-1\"}, \"admin\": true, {\"x\":\"",
        "STU-1\\",
        "STU-12\"",
        "stu-1001",
        "STU-",
        "STU-12a",
        "STU-1001 ",
        "1001",
        "",
        "STU-1\nSTU-2"
    })
    @DisplayName("buildRequest: STU-<숫자> 형식이 아닌 studentId(따옴표·역슬래시 포함)는 IllegalArgumentException")
    void rejectsInvalidStudentId(String studentId) {
        assertThatThrownBy(() -> client.buildRequest(1, studentId))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("buildRequest: studentId 가 null 이면 IllegalArgumentException")
    void rejectsNullStudentId() {
        assertThatThrownBy(() -> client.buildRequest(1, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("buildRequest: submissionId 가 null 이면 IllegalArgumentException")
    void rejectsNullSubmissionId() {
        assertThatThrownBy(() -> client.buildRequest(null, "STU-1001"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("buildRequest: 유효한 studentId 는 JSON 으로 파싱해도 원문 그대로이고 추가 필드가 생기지 않는다")
    void validStudentIdRoundTripsWithoutInjection() throws Exception {
        JsonNode body = MAPPER.readTree(bodyOf(client.buildRequest(7, "STU-0")));

        assertThat(body.get("studentId").asText()).isEqualTo("STU-0");
        assertThat(body.has("admin")).isFalse();
        assertThat(body.size()).isEqualTo(2);
    }

    private static String bodyOf(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return out.toString(StandardCharsets.UTF_8);
    }
}
