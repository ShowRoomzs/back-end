package showroomz.global.delivery.tracker.sweettracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.sweettracker.dto.SweetTrackerTrackingResponse;
import showroomz.global.delivery.tracker.sweettracker.dto.SweetTrackerUsageResponse;

import java.time.Duration;
import java.util.Map;

/**
 * 스마트택배 조회 API 호출(택배 추적 설계서 2절) — HTTP 만 한다. 판정은 {@link SweetTrackerDeliveryTracker}의 몫이다.
 *
 * <p>요청은 JSON 본문이다 — 폼 본문은 415, 쿼리 파라미터만으로는 400 이다(2026-10-05 실호출 확인).
 * API 키는 본문({@code t_key})으로만 나간다 — URL 에 싣지 않으므로 통신 오류 메시지에 섞이지 않는다.
 * 에러 응답(101~106)은 HTTP 200 에 {@code status=false · code · msg}로 오므로 상태 코드로 예외를 던지지 않는다.
 * 통신 오류·해석 불가 응답은 {@link RuntimeException}이다.
 */
@Component
@ConditionalOnProperty(prefix = "delivery.tracker", name = "enabled", havingValue = "true")
public class SweetTrackerClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final RestClient restClient;

    @Autowired
    public SweetTrackerClient(DeliveryTrackerProperties properties) {
        this(properties.getApiKey(), RestClient.builder()
                .baseUrl(properties.getApiUrl())
                .requestFactory(timeoutFactory())
                .build());
    }

    /** HTTP 클라이언트를 바꿔 끼울 때(테스트의 MockRestServiceServer 등) — 키 검사는 같다. */
    public SweetTrackerClient(String apiKey, RestClient restClient) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "delivery.tracker.api-key 설정이 비어 있습니다. delivery.tracker.enabled=false 로 끄거나 값을 채우세요.");
        }
        this.apiKey = apiKey;
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    public SweetTrackerTrackingResponse trackingInfo(String carrierCode, String trackingNumber) {
        return post("/api/v1/trackingInfo",
                Map.of("t_key", apiKey, "t_code", carrierCode, "t_invoice", trackingNumber),
                SweetTrackerTrackingResponse.class);
    }

    public SweetTrackerUsageResponse usage() {
        return post("/api/v1/key/usage", Map.of("t_key", apiKey), SweetTrackerUsageResponse.class);
    }

    private <T> T post(String path, Map<String, String> body, Class<T> type) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    // 업체 에러(101~106)는 HTTP 200 으로 온다 — 그 밖의 상태는 장애다. 스프링 에러 JSON 의
                    // {"status":415} 가 Boolean status=true 로 읽혀 「정상 · 이벤트 없음」이 되지 않게 본문 전에 끊는다.
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("스마트택배 HTTP " + response.getStatusCode().value());
                    }
                    return MAPPER.readValue(response.getBody(), type);
                });
    }
}
