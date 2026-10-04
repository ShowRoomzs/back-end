package showroomz.global.delivery.tracker.sweettracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import showroomz.global.config.properties.DeliveryTrackerProperties;
import showroomz.global.delivery.tracker.sweettracker.dto.SweetTrackerTrackingResponse;
import showroomz.global.delivery.tracker.sweettracker.dto.SweetTrackerUsageResponse;

import java.time.Duration;

/**
 * 스마트택배 조회 API 호출(택배 추적 설계서 2절) — HTTP 만 한다. 판정은 {@link SweetTrackerDeliveryTracker}의 몫이다.
 *
 * <p>API 키는 폼 본문({@code t_key})으로만 나간다 — URL 에 싣지 않으므로 통신 오류 메시지에 섞이지 않는다.
 * 에러 응답(101~106)은 HTTP 상태와 무관하게 본문으로 판정하므로 상태 코드로 예외를 던지지 않는다.
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

    SweetTrackerClient(String apiKey, RestClient restClient) {
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
        MultiValueMap<String, String> form = keyForm();
        form.add("t_code", carrierCode);
        form.add("t_invoice", trackingNumber);
        return post("/api/v1/trackingInfo", form, SweetTrackerTrackingResponse.class);
    }

    public SweetTrackerUsageResponse usage() {
        return post("/api/v1/key/usage", keyForm(), SweetTrackerUsageResponse.class);
    }

    private MultiValueMap<String, String> keyForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("t_key", apiKey);
        return form;
    }

    private <T> T post(String path, MultiValueMap<String, String> form, Class<T> type) {
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange((request, response) -> MAPPER.readValue(response.getBody(), type));
    }
}
