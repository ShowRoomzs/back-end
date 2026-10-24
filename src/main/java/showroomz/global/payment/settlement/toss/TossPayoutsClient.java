package showroomz.global.payment.settlement.toss;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import showroomz.global.config.properties.TossPayoutsProperties;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Balance;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.ErrorBody;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Page;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Payout;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.PayoutRequest;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.Seller;
import showroomz.global.payment.settlement.toss.TossPayoutsDtos.SellerRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 토스 지급대행 v2 HTTP 클라이언트(44 토스 설계서 1-1 · 1-2 · 6-2) — {@code RestClient} 1개 · Basic 인증 · 본문 있는 POST 는
 * {@code TossPayments-api-security-mode: ENCRYPTION} + JWE · {@code Idempotency-Key}. 응답은 암호화 호출이면 2xx · 4xx 모두 JWE 다 —
 * 복호화해 Jackson 으로 읽는다(복호화가 안 되고 JSON 이면 평문으로 받아 준다 · 401 등).
 *
 * <p>실패의 종류를 가른다 — 토스가 코드로 거절한 4xx 는 {@link PaymentGatewayRejectedException}(결과 확정), 통신 · 5xx · 429 · 409 ·
 * 복호화 · 해석 불가 · 「처리 중」 계열 코드는 {@link PaymentGatewayException}(결과 모름). 시크릿 · 보안 키 · 복호화 전 본문은 로그에 싣지
 * 않는다.
 */
@Slf4j
public class TossPayoutsClient {

    static final String SECURITY_MODE_HEADER = "TossPayments-api-security-mode";
    static final String SECURITY_MODE_ENCRYPTION = "ENCRYPTION";
    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int LIST_LIMIT = 1_000;
    private static final int LIST_MAX_PAGES = 50;

    private final RestClient rest;
    private final TossJwe jwe;
    private final ObjectMapper objectMapper;

    /**
     * @param builder 요청 팩토리는 호출자가 정한다 — 운영은 {@link #requestFactory} (타임아웃), 테스트는 {@code MockRestServiceServer} 가 묶은 빌더
     */
    public TossPayoutsClient(TossPayoutsProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.jwe = new TossJwe(properties.getSecurityKey());
        this.objectMapper = objectMapper;
        String basic = Base64.getEncoder().encodeToString((properties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.rest = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    /** 설정의 연결 · 읽기 타임아웃을 단 요청 팩토리 — 기동 설정이 빌더에 끼운다. */
    public static SimpleClientHttpRequestFactory requestFactory(TossPayoutsProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return factory;
    }

    // ------------------------------------------------------------------ 잔액

    public Balance balance() {
        return read(call(HttpMethod.GET, "/v2/balances", null, null, false), Balance.class, "잔액 조회");
    }

    // ------------------------------------------------------------------ 셀러

    public Seller createSeller(SellerRequest request, String idempotencyKey) {
        return read(call(HttpMethod.POST, "/v2/sellers", request, idempotencyKey, true), Seller.class, "셀러 등록");
    }

    /** 바꿀 필드만 — {@code null} 은 내보내지 않는다(1-3). */
    public Seller updateSeller(String sellerId, Map<String, Object> changes) {
        return read(call(HttpMethod.POST, "/v2/sellers/" + sellerId, changes, null, true), Seller.class, "셀러 수정");
    }

    public Seller getSeller(String sellerId) {
        return read(call(HttpMethod.GET, "/v2/sellers/" + sellerId, null, null, false), Seller.class, "셀러 조회");
    }

    /** 목록을 커서로 훑어 {@code refSellerId} 가 같은 셀러 — id 복구(0-5 · Q7). 없으면 null. */
    public Seller findSellerByRef(String refSellerId) {
        String cursor = null;
        for (int page = 0; page < LIST_MAX_PAGES; page++) {
            String path = "/v2/sellers?limit=" + LIST_LIMIT + (cursor == null ? "" : "&startingAfter=" + cursor);
            Page<Seller> result = readPage(call(HttpMethod.GET, path, null, null, false), Seller.class, "셀러 목록 조회");
            for (Seller seller : result.itemsOrEmpty()) {
                if (refSellerId.equals(seller.refSellerId())) {
                    return seller;
                }
            }
            if (!Boolean.TRUE.equals(result.hasMore()) || result.nextCursor() == null) {
                return null;
            }
            cursor = result.nextCursor();
        }
        return null;
    }

    // ------------------------------------------------------------------ 지급

    /** 1건 요청 — 본문은 배열(응답이 {@code payout-list} 다 · Q11). 응답이 단건이면 그대로 받는다. */
    public Payout requestPayout(PayoutRequest request, String idempotencyKey) {
        String body = call(HttpMethod.POST, "/v2/payouts", List.of(request), idempotencyKey, true);
        JsonNode root = tree(body, "지급 요청");
        JsonNode node = root;
        if (root.has("entityBody")) {
            node = root.get("entityBody");
        }
        if (node.has("items") && node.get("items").isArray() && !node.get("items").isEmpty()) {
            node = node.get("items").get(0);
        }
        return convert(node, Payout.class, "지급 요청");
    }

    public Payout getPayout(String payoutId) {
        return read(call(HttpMethod.GET, "/v2/payouts/" + payoutId, null, null, false), Payout.class, "지급 조회");
    }

    public Payout cancelPayout(String payoutId) {
        return read(call(HttpMethod.POST, "/v2/payouts/" + payoutId + "/cancel", null, null, false), Payout.class, "지급 취소");
    }

    /** 지급일 범위의 지급 전부(커서 끝까지) — 대사(5-3) · id 복구(5-4 ②). */
    public List<Payout> listPayouts(LocalDate from, LocalDate to) {
        List<Payout> all = new java.util.ArrayList<>();
        String cursor = null;
        for (int page = 0; page < LIST_MAX_PAGES; page++) {
            String path = "/v2/payouts?limit=" + LIST_LIMIT + "&payoutDateGte=" + from + "&payoutDateLte=" + to
                    + (cursor == null ? "" : "&startingAfter=" + cursor);
            Page<Payout> result = readPage(call(HttpMethod.GET, path, null, null, false), Payout.class, "지급 목록 조회");
            all.addAll(result.itemsOrEmpty());
            if (!Boolean.TRUE.equals(result.hasMore()) || result.nextCursor() == null) {
                break;
            }
            cursor = result.nextCursor();
        }
        return all;
    }

    // ------------------------------------------------------------------ 호출 · 해석

    /**
     * @return 해석 가능한 평문 JSON(복호화 뒤)
     * @throws PaymentGatewayRejectedException 토스가 코드로 거절(4xx · 결과 확정)
     * @throws PaymentGatewayException         결과 모름
     */
    private String call(HttpMethod method, String path, Object body, String idempotencyKey, boolean encrypted) {
        RestClient.RequestBodySpec spec = rest.method(method).uri(path).accept(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            spec = spec.header(IDEMPOTENCY_HEADER, idempotencyKey);
        }
        if (body != null) {
            String json;
            try {
                json = objectMapper.writeValueAsString(body);
            } catch (IOException e) {
                throw new IllegalStateException("토스 지급대행 요청 직렬화 실패", e);
            }
            if (encrypted) {
                // 본문 = JWE compact 문자열 그대로(가이드 샘플 · Q11 로 Content-Type 확정).
                spec = spec.header(SECURITY_MODE_HEADER, SECURITY_MODE_ENCRYPTION).contentType(MediaType.TEXT_PLAIN)
                        .body(jwe.encrypt(json));
            } else {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(json);
            }
        }
        try {
            return spec.exchange((request, response) -> {
                int status = response.getStatusCode().value();
                String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                String plain = plain(raw, encrypted);
                if (status >= 200 && status < 300) {
                    if (plain == null) {
                        throw new PaymentGatewayException("토스 지급대행 " + path + " 응답 해석 불가(복호화 실패)");
                    }
                    return plain;
                }
                ErrorBody error = plain == null ? null : convertQuietly(plain, ErrorBody.class);
                String code = error == null ? null : error.code();
                String message = error == null ? null : error.message();
                if (TossPayoutFailureClassifier.isUnknownOutcome(status, code)) {
                    throw new PaymentGatewayException("토스 지급대행 " + path + " 결과 모름 — HTTP " + status + " " + code
                            + (message == null ? "" : " - " + message));
                }
                throw new PaymentGatewayRejectedException(status, code == null ? "HTTP_" + status : code,
                        "토스 지급대행 " + path + (message == null ? "" : " - " + message));
            });
        } catch (ResourceAccessException e) {
            throw new PaymentGatewayException("토스 지급대행 " + path + " 통신 실패 — " + e.getMessage(), e);
        }
    }

    /** 암호화 호출의 응답은 JWE — 복호화. 안 되면 평문 JSON 인지 본다(401 · 게이트웨이 오류). 둘 다 아니면 null. */
    private String plain(String raw, boolean encrypted) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String trimmed = raw.trim();
        if (encrypted && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            try {
                return jwe.decrypt(trimmed);
            } catch (IllegalArgumentException e) {
                log.warn("토스 지급대행 응답 복호화 실패 — 평문으로 다시 본다: {}", e.getMessage());
            }
        }
        return trimmed.startsWith("{") || trimmed.startsWith("[") ? trimmed : null;
    }

    private <T> T read(String json, Class<T> type, String action) {
        JsonNode root = tree(json, action);
        return convert(root.has("entityBody") && !root.has("id") ? root.get("entityBody") : root, type, action);
    }

    private <T> Page<T> readPage(String json, Class<T> itemType, String action) {
        JsonNode root = tree(json, action);
        JsonNode node = root.has("entityBody") ? root.get("entityBody") : root;
        try {
            Page<JsonNode> page = objectMapper.readValue(objectMapper.treeAsTokens(node), new TypeReference<Page<JsonNode>>() {});
            List<T> items = page.itemsOrEmpty().stream().map(n -> convert(n, itemType, action)).toList();
            return new Page<>(page.hasMore(), page.nextCursor(), items);
        } catch (IOException e) {
            throw new PaymentGatewayException("토스 지급대행 " + action + " 목록 해석 실패", e);
        }
    }

    private JsonNode tree(String json, String action) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new PaymentGatewayException("토스 지급대행 " + action + " 응답 해석 실패", e);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type, String action) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (IOException e) {
            throw new PaymentGatewayException("토스 지급대행 " + action + " 응답 해석 실패", e);
        }
    }

    private <T> T convertQuietly(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            return null;
        }
    }
}
