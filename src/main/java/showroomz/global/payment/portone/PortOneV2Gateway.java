package showroomz.global.payment.portone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 포트원 V2 REST 구현(결제 계획서 6-1). 서버 SDK 대신 Spring {@code RestClient}를 쓴다 — 서비스 계층은
 * {@link PortOnePaymentGateway}만 보므로 SDK로 바꿔도 이 클래스만 바뀐다.
 *
 * <p>실패의 종류를 가른다 — 4xx는 {@link PaymentGatewayRejectedException}(결과 확정), 타임아웃·통신 오류·5xx는
 * {@link PaymentGatewayException}(결과 모름). 취소에서 이 구분이 돈이다(2-3 ⑧).
 * API Secret은 로그·예외 메시지에 싣지 않는다(6-3).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "portone", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PortOneV2Gateway implements PortOnePaymentGateway {

    private static final int LIST_PAGE_SIZE = 100;
    private static final int LIST_MAX_PAGES = 200;

    private final PortOneProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public PortOneV2Gateway(PortOneProperties properties, ObjectMapper objectMapper) {
        requireText(properties.getStoreId(), "portone.store-id");
        requireText(properties.getApiSecret(), "portone.api-secret");
        requireText(properties.getWebhookSecret(), "portone.webhook-secret");
        requireText(properties.getChannelKeys().getCard(), "portone.channel-keys.card");
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        this.client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + properties.getApiSecret())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 설정이 비어 있습니다. portone.enabled=false 로 끄거나 값을 채우세요.");
        }
    }

    @Override
    public String storeId() {
        return properties.getStoreId();
    }

    @Override
    public void preRegister(String paymentId, long totalAmount, String currency) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("storeId", properties.getStoreId());
        body.put("totalAmount", totalAmount);
        body.put("currency", currency);
        Response response = exchange("POST", uri("/payments/" + encode(paymentId) + "/pre-register"), body.toString());
        if (response.is2xx()) {
            return;
        }
        throw rejectedOrUnknown("사전 등록", response);
    }

    @Override
    public Optional<PortOnePayment> getPayment(String paymentId) {
        Response response = exchange("GET", uri("/payments/" + encode(paymentId) + "?storeId=" + encode(properties.getStoreId())), null);
        if (response.status == 404) {
            return Optional.empty();
        }
        if (!response.is2xx()) {
            throw rejectedOrUnknown("결제 조회", response);
        }
        return Optional.of(parsePayment(response.body));
    }

    @Override
    public PortOneCancelResult cancel(String paymentId, long amount, String reason) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("storeId", properties.getStoreId());
        body.put("reason", reason == null || reason.isBlank() ? "주문 취소" : reason);
        // amount 를 비우면 전액 취소다. 현재 취소 가능 금액을 함께 보내 이미 부분 취소된 결제가 있으면 PG 가 거절하게 한다.
        body.put("currentCancellableAmount", amount);
        Response response = exchange("POST", uri("/payments/" + encode(paymentId) + "/cancel"), body.toString());
        if (response.is2xx()) {
            JsonNode cancellation = readTree(response.body).path("cancellation");
            String status = cancellation.path("status").asText("");
            String pgCancellationId = textOrNull(cancellation, "pgCancellationId");
            PortOneCancelResult.Outcome outcome = "SUCCEEDED".equals(status)
                    ? PortOneCancelResult.Outcome.SUCCEEDED
                    : PortOneCancelResult.Outcome.PENDING;
            if ("FAILED".equals(status)) {
                throw new PaymentGatewayRejectedException(200, "CANCELLATION_FAILED", cancellation.toString());
            }
            return new PortOneCancelResult(outcome, pgCancellationId, response.body);
        }
        if (response.status >= 400 && response.status < 500) {
            String type = readTree(response.body).path("type").asText("");
            if ("PAYMENT_ALREADY_CANCELLED".equals(type)) {
                return new PortOneCancelResult(PortOneCancelResult.Outcome.ALREADY_CANCELLED, null, response.body);
            }
        }
        throw rejectedOrUnknown("결제 취소", response);
    }

    @Override
    public List<PortOnePayment> listPayments(LocalDateTime from, LocalDateTime until, List<PortOneStatus> statuses) {
        List<PortOnePayment> result = new ArrayList<>();
        for (int page = 0; page < LIST_MAX_PAGES; page++) {
            ObjectNode request = objectMapper.createObjectNode();
            request.putObject("page").put("number", page).put("size", LIST_PAGE_SIZE);
            ObjectNode filter = request.putObject("filter");
            filter.put("storeId", properties.getStoreId());
            filter.put("timestampType", "CREATED_AT");
            filter.put("from", toIso(from));
            filter.put("until", toIso(until));
            var statusArray = filter.putArray("status");
            statuses.forEach(status -> statusArray.add(status.name()));

            Response response = exchange("GET", uri("/payments?requestBody=" + encode(request.toString())), null);
            if (!response.is2xx()) {
                throw rejectedOrUnknown("결제 목록 조회", response);
            }
            JsonNode items = readTree(response.body).path("items");
            for (JsonNode item : items) {
                result.add(parsePayment(item.toString()));
            }
            if (items.size() < LIST_PAGE_SIZE) {
                break;
            }
        }
        return result;
    }

    @Override
    public Optional<String> channelKeyFor(PaymentMethod method, EasyPayProvider easyPayProvider) {
        PortOneProperties.ChannelKeys keys = properties.getChannelKeys();
        String key;
        if (method == PaymentMethod.CARD) {
            key = keys.getCard();
        } else if (easyPayProvider == null) {
            return Optional.empty();
        } else {
            key = switch (easyPayProvider) {
                case KAKAOPAY -> keys.getKakaopay();
                case NAVERPAY -> keys.getNaverpay();
                case TOSSPAY -> keys.getTosspay();
            };
            // 비워 두면 카드 채널을 따라간다(허브형 — 6-2). "-" 는 명시적으로 끈 것이다.
            if (key == null || key.isBlank()) {
                key = keys.getCard();
            }
        }
        if (key == null || key.isBlank() || PortOneProperties.DISABLED_CHANNEL.equals(key.trim())) {
            return Optional.empty();
        }
        return Optional.of(key.trim());
    }

    // ------------------------------------------------------------------ HTTP

    /**
     * URI 는 직접 조립한다 — {@code RestClient.uri(String)} 은 문자열을 URI 템플릿으로 다뤄 목록 조회의 JSON 파라미터({@code {}})를
     * 변수로 오해하고, 이미 인코딩된 값을 다시 인코딩한다. {@link URI} 객체를 넘기면 그대로 쓴다.
     */
    private URI uri(String pathAndQuery) {
        return URI.create(properties.getBaseUrl() + pathAndQuery);
    }

    private Response exchange(String method, URI uri, String jsonBody) {
        try {
            RestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(uri);
            if (jsonBody != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
            }
            return spec.exchange((request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                return new Response(status.value(), body);
            });
        } catch (ResourceAccessException e) {
            // 타임아웃·연결 실패 — 결과를 모른다. 헤더(시크릿)는 메시지에 넣지 않는다.
            throw new PaymentGatewayException("포트원 통신 실패: " + method + " " + uri.getPath(), e);
        }
    }

    private RuntimeException rejectedOrUnknown(String action, Response response) {
        if (response.status >= 400 && response.status < 500) {
            JsonNode node = readTree(response.body);
            String type = node.path("type").asText("UNKNOWN");
            String message = node.path("message").asText(null);
            log.warn("포트원 {} 거절 - status: {}, type: {}", action, response.status, type);
            return new PaymentGatewayRejectedException(response.status, type, message);
        }
        log.error("포트원 {} 실패 - status: {}", action, response.status);
        return new PaymentGatewayException("포트원 " + action + " 실패: HTTP " + response.status);
    }

    private JsonNode readTree(String json) {
        try {
            return json == null || json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (IOException e) {
            return objectMapper.createObjectNode();
        }
    }

    private PortOnePayment parsePayment(String json) {
        JsonNode node = readTree(json);
        JsonNode failure = node.path("failure");
        String failCode = textOrNull(failure, "pgCode");
        if (failCode == null) {
            failCode = textOrNull(failure, "reason");
        }
        String failMessage = textOrNull(failure, "pgMessage");
        if (failMessage == null) {
            failMessage = textOrNull(failure, "reason");
        }
        JsonNode amount = node.path("amount");
        return new PortOnePayment(
                textOrNull(node, "id"),
                PortOneStatus.of(textOrNull(node, "status")),
                textOrNull(node, "storeId"),
                textOrNull(node, "currency"),
                amount.hasNonNull("total") ? amount.get("total").asLong() : null,
                textOrNull(node, "transactionId"),
                textOrNull(node.path("channel"), "pgProvider"),
                node.hasNonNull("method") ? node.get("method").toString() : null,
                parseTime(textOrNull(node, "paidAt")),
                parseTime(textOrNull(node, "cancelledAt")),
                failCode,
                failMessage,
                json
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static LocalDateTime parseTime(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private static String toIso(LocalDateTime local) {
        return local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** 경로·쿼리 공용 — 공백은 {@code +} 가 아니라 {@code %20} 이어야 경로에서도 맞다. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record Response(int status, String body) {
        boolean is2xx() {
            return status >= 200 && status < 300;
        }
    }
}
