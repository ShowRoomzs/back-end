package showroomz.global.payment.settlement.inicis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import showroomz.global.config.properties.InicisPayoutsProperties;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.AmlStatus;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.PayCheck;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.PayRequest;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.PayResult;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.SubMallRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 이니시스 지급대행(신버전 AML) HTTP 클라이언트(60 전환 계획서 1-2 · D-11) — {@code RestClient} 1개 · JSON UTF-8 · 헤더
 * {@code X-API-KEY} · {@code X-Request-ID}(호출마다 UUID — 결과에 실어 돌려준다. 이니시스 문의 때 쓴다).
 *
 * <p>실패의 종류를 가른다 — 응답의 {@code code} 가 성공 코드가 아니면 {@link PaymentGatewayRejectedException}(결과 확정 · {@code type} =
 * 이니시스 코드 · 메시지 = {@code msg}), 통신 실패 · 5xx · 429 · 해석 불가는 {@link PaymentGatewayException}(결과 모름).
 * <b>요청 · 응답 본문은 로그에 싣지 않는다</b> — ENC 가 Base64 뿐이면 계좌 · 생년월일이 평문과 같다. 로그는 {@code idMall} · 날짜 · 금액 ·
 * 코드 · {@code X-Request-ID} 만.
 *
 * <p>GET 3종의 파라미터는 쿼리스트링으로 보낸다(매뉴얼 미기재 · 가정 · Q4).
 */
@Slf4j
public class InicisPayoutsClient {

    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String BASE_PATH = "/rest/api/openmall";
    static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient rest;
    private final ObjectMapper objectMapper;
    private final String merchantId;

    /**
     * @param builder 요청 팩토리는 호출자가 정한다 — 운영은 {@link #requestFactory}(타임아웃), 테스트는 {@code MockRestServiceServer} 가 묶은 빌더
     */
    public InicisPayoutsClient(InicisPayoutsProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.merchantId = properties.getMerchantId();
        this.rest = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(API_KEY_HEADER, properties.getApiKey() == null ? "" : properties.getApiKey())
                .build();
    }

    public static SimpleClientHttpRequestFactory requestFactory(InicisPayoutsProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return factory;
    }

    public String merchantId() {
        return merchantId;
    }

    /** 호출 결과와 그 호출의 {@code X-Request-ID}. */
    public record Reply<T>(T body, String requestId) {
    }

    // ------------------------------------------------------------------ 서브몰(1-4)

    public Reply<Void> upsert(SubMallRequest request) {
        Reply<JsonNode> reply = call(HttpMethod.POST, "/upsert", request, null,
                InicisPayoutFailureClassifier.SUCCESS_UPSERT, "서브몰 등록");
        log.info("이니시스 서브몰 {} 접수 - idMall: {}, requestId: {}", request.svcDmlCd(), request.idMall(), reply.requestId());
        return new Reply<>(null, reply.requestId());
    }

    public Reply<AmlStatus> amlStatus(String idMall) {
        Reply<JsonNode> reply = call(HttpMethod.GET, "/amlstatus", null,
                params("idMerchant", merchantId, "idMall", idMall, "svcType", InicisPayoutsDtos.SVC_REQUEST),
                InicisPayoutFailureClassifier.SUCCESS_AMLSTATUS, "AML 상태 조회");
        return new Reply<>(convert(first(reply.body()), AmlStatus.class, "AML 상태 조회"), reply.requestId());
    }

    // ------------------------------------------------------------------ 지급(1-3 · 1-5)

    public Reply<Void> payRequest(PayRequest request) {
        Reply<JsonNode> reply = call(HttpMethod.POST, "/payreq", request, null,
                InicisPayoutFailureClassifier.SUCCESS_PAYREQ, "지급 요청");
        log.info("이니시스 지급 {} 접수 - idMall: {}, dtPay: {}, amount: {}, requestId: {}", request.svcDmlCd(),
                request.idMall(), request.dtPay(), request.amtSupply(), reply.requestId());
        return new Reply<>(null, reply.requestId());
    }

    /** 지급일 범위의 송금 결과(서브몰 전체 · 익일지급 · 지급). */
    public Reply<List<PayResult>> payResults(LocalDate from, LocalDate to, String clService) {
        Reply<JsonNode> reply = call(HttpMethod.GET, "/payresult", null,
                params("idMerchant", merchantId, "startDtPay", from.format(DATE), "endDtPay", to.format(DATE),
                        "clService", clService, "svcType", InicisPayoutsDtos.SVC_REQUEST),
                InicisPayoutFailureClassifier.SUCCESS_PAYRESULT, "송금 결과 조회");
        JsonNode data = reply.body();
        List<PayResult> results;
        if (data == null || data.isNull() || data.isMissingNode()) {
            results = List.of();
        } else if (data.isArray()) {
            results = convert(data, new TypeReference<List<PayResult>>() {}, "송금 결과 조회");
        } else {
            results = List.of(convert(data, PayResult.class, "송금 결과 조회"));
        }
        return new Reply<>(results, reply.requestId());
    }

    // ------------------------------------------------------------------ 자금(1-6)

    public Reply<PayCheck> payCheck(LocalDate from, LocalDate to) {
        Reply<JsonNode> reply = call(HttpMethod.GET, "/paycheck", null,
                params("idMerchant", merchantId, "startDtPay", from.format(DATE), "endDtPay", to.format(DATE)),
                InicisPayoutFailureClassifier.SUCCESS_PAYCHECK, "잔액 대사");
        return new Reply<>(convert(first(reply.body()), PayCheck.class, "잔액 대사"), reply.requestId());
    }

    // ------------------------------------------------------------------ 호출 · 해석

    /**
     * @return 응답의 {@code data}(없으면 null)와 요청 id
     * @throws PaymentGatewayRejectedException {@code code} 가 성공 코드가 아니다(결과 확정)
     * @throws PaymentGatewayException         결과 모름
     */
    private Reply<JsonNode> call(HttpMethod method, String path, Object body, Map<String, String> query, String successCode,
                                 String action) {
        String requestId = UUID.randomUUID().toString();
        Function<UriBuilder, java.net.URI> uri = builder -> {
            UriBuilder b = builder.path(BASE_PATH + path);
            if (query != null) {
                query.forEach(b::queryParam);
            }
            return b.build();
        };
        RestClient.RequestBodySpec spec = rest.method(method).uri(uri).header(REQUEST_ID_HEADER, requestId)
                .accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            String json;
            try {
                json = objectMapper.writeValueAsString(body);
            } catch (IOException e) {
                throw new IllegalStateException("이니시스 지급대행 요청 직렬화 실패", e);
            }
            spec = spec.contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8)).body(json);
        }
        try {
            JsonNode data = spec.exchange((request, response) -> {
                int status = response.getStatusCode().value();
                String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode root = parse(raw);
                String code = root == null ? null : root.path("code").asText(null);
                String message = root == null ? null : root.path("msg").asText(null);
                if (status >= 500 || status == 429) {
                    throw new PaymentGatewayException("이니시스 " + action + " 결과 모름 — HTTP " + status + " " + code
                            + " · requestId " + requestId);
                }
                if (root == null || code == null) {
                    if (status >= 400) {
                        throw new PaymentGatewayRejectedException(status, "HTTP_" + status,
                                "이니시스 " + action + " 거절 · requestId " + requestId);
                    }
                    throw new PaymentGatewayException("이니시스 " + action + " 응답 해석 불가 — HTTP " + status
                            + " · requestId " + requestId);
                }
                if (!successCode.equals(code)) {
                    log.warn("이니시스 {} 거절 - code: {}, msg: {}, requestId: {}", action, code, message, requestId);
                    throw new PaymentGatewayRejectedException(status, code,
                            "이니시스 " + action + (message == null ? "" : " - " + message) + " · requestId " + requestId);
                }
                JsonNode dataNode = root.get("data");
                return dataNode == null || dataNode.isNull() ? null : dataNode;
            });
            return new Reply<>(data, requestId);
        } catch (ResourceAccessException e) {
            throw new PaymentGatewayException("이니시스 " + action + " 통신 실패 — " + e.getMessage() + " · requestId " + requestId, e);
        }
    }

    /** 쿼리 파라미터 — 적은 순서대로(로그 · 테스트가 읽기 쉽게). */
    private static Map<String, String> params(String... keyValues) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return map;
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node != null && node.isObject() ? node : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonNode first(JsonNode data) {
        if (data != null && data.isArray()) {
            return data.isEmpty() ? null : data.get(0);
        }
        return data;
    }

    private <T> T convert(JsonNode node, Class<T> type, String action) {
        if (node == null) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, type);
        } catch (IOException e) {
            throw new PaymentGatewayException("이니시스 " + action + " 응답 해석 실패", e);
        }
    }

    private <T> T convert(JsonNode node, TypeReference<T> type, String action) {
        try {
            return objectMapper.readValue(objectMapper.treeAsTokens(node), type);
        } catch (IOException e) {
            throw new PaymentGatewayException("이니시스 " + action + " 응답 해석 실패", e);
        }
    }
}
