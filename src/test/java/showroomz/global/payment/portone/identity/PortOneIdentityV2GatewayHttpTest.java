package showroomz.global.payment.portone.identity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.portone.sdk.server.errors.ForbiddenException;
import io.portone.sdk.server.errors.InvalidRequestException;
import io.portone.sdk.server.errors.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.payment.portone.PaymentGatewayRejectedException;
import showroomz.global.payment.portone.PortOneProperties;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포트원 V2 본인인증 게이트웨이 — <b>실제 서버 SDK</b>가 HTTP 로 부르는 것을 JDK {@link HttpServer} 가 받는다(57 테스트 시나리오 GW 절).
 *
 * <p>SDK 객체를 직접 만드는 {@code PortOneIdentityV2GatewayTest}(매핑)와 달리, 여기서는 ① SDK 가 실제로 보내는 경로 · 쿼리 · 헤더 ②
 * 포트원 응답 JSON(문서 · SDK 소스의 필드명 그대로)이 우리 모델까지 오는지 ③ 오류 본문 {@code type} 별로 어떤 예외가 되는지 ④
 * 타임아웃 · 연결 실패가 「결과 모름」이 되는지를 본다. JSON 모양은 서버 SDK 0.24.0 소스(`@SerialName` · `@JsonClassDiscriminator`)
 * 기준이다 — SDK 를 올리면 이 테스트가 먼저 깨진다.
 */
@DisplayName("[57 GW] 포트원 본인인증 게이트웨이 — 실 SDK · HTTP 스텁")
class PortOneIdentityV2GatewayHttpTest {

    private static final String SECRET = "secret-test";
    private static final String STORE_ID = "store-test";
    private static final String CHANNEL_KEY = "channel-key-identity-test";

    private HttpServer server;
    /** 핸들러를 디스패처 밖에서 돌린다 — 타임아웃 케이스의 sleep 이 서버 종료를 붙잡지 않게 shutdownNow 로 끊는다. */
    private ExecutorService handlers;
    private boolean stopped;
    private PortOneIdentityV2Gateway gateway;
    private final AtomicReference<Recorded> lastRequest = new AtomicReference<>();
    private volatile Responder responder;

    record Recorded(String method, String path, String query, String authorization, String accept) {}

    @FunctionalInterface
    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequest.set(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                    exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Accept")));
            responder.respond(exchange);
        });
        handlers = Executors.newCachedThreadPool();
        server.setExecutor(handlers);
        server.start();
        gateway = new PortOneIdentityV2Gateway(properties(500));
    }

    @AfterEach
    void stopServer() {
        gateway.close();
        handlers.shutdownNow();
        stopServerOnce();
    }

    private void stopServerOnce() {
        if (!stopped) {
            stopped = true;
            server.stop(0);
        }
    }

    private PortOneProperties properties(int readTimeoutMillis) {
        PortOneProperties p = new PortOneProperties();
        p.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        p.setStoreId(STORE_ID);
        p.setApiSecret(SECRET);
        p.getChannelKeys().setIdentity(CHANNEL_KEY);
        p.setConnectTimeoutMillis(500);
        p.setReadTimeoutMillis(readTimeoutMillis);
        return p;
    }

    private void respond(int status, String body) {
        responder = exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        };
    }

    // ------------------------------------------------------------------ 포트원 응답 본문(문서 · SDK 소스의 필드명)

    private static final String CHANNEL_JSON = """
            {"type":"TEST","id":"ch-1","key":"channel-key-identity-test","name":"KCP 본인인증","pgProvider":"KCP_V2","pgMerchantId":"mid-1"}""";

    private static String verifiedJson(String customerJson) {
        return """
                {"status":"VERIFIED","id":"idv01A","channel":%s,
                 "verifiedCustomer":%s,
                 "requestedAt":"2026-10-24T01:00:00Z","updatedAt":"2026-10-24T01:03:12Z","statusChangedAt":"2026-10-24T01:03:12Z",
                 "verifiedAt":"2026-10-24T01:03:12Z","pgTxId":"pgtx-1","pgRawResponse":"{\\"raw\\":true}","version":"V2"}
                """.formatted(CHANNEL_JSON, customerJson);
    }

    private static final String FULL_CUSTOMER = """
            {"id":"cust-1","name":"홍길동","operator":"LGU_MVNO","phoneNumber":"01012345678","birthDate":"1998-04-12",
             "gender":"FEMALE","isForeigner":false,"ci":"CI-VALUE==","di":"DI-VALUE=="}""";

    private static final String REQUESTED_CUSTOMER = """
            {"id":null,"name":null,"phoneNumber":null,"birthYear":null,"birthMonth":null,"birthDay":null}""";

    // ================================================================== 요청

    @Nested
    @DisplayName("요청 모양")
    class RequestShape {

        @Test
        @DisplayName("GW-01 GET /identity-verifications/{id}?storeId=… · Authorization: PortOne {secret} · Accept JSON")
        void requestLine() {
            respond(200, verifiedJson(FULL_CUSTOMER));

            gateway.find("idv01A");

            Recorded r = lastRequest.get();
            assertThat(r.method()).isEqualTo("GET");
            assertThat(r.path()).isEqualTo("/identity-verifications/idv01A");
            assertThat(r.query()).isEqualTo("storeId=" + STORE_ID);
            assertThat(r.authorization()).isEqualTo("PortOne " + SECRET);
            assertThat(r.accept()).contains("application/json");
        }

        @Test
        @DisplayName("GW-02 storeId() · channelKey() 는 설정값 — 앱에 내려 주는 값이다")
        void exposesConfig() {
            assertThat(gateway.storeId()).isEqualTo(STORE_ID);
            assertThat(gateway.channelKey()).isEqualTo(CHANNEL_KEY);
        }
    }

    // ================================================================== 응답 3종

    @Nested
    @DisplayName("응답 본문 → 모델")
    class ResponseBody {

        @Test
        @DisplayName("GW-10 VERIFIED — verifiedCustomer 전 필드 · verifiedAt(UTC) → KST LocalDateTime · pgTxId")
        void verified() {
            respond(200, verifiedJson(FULL_CUSTOMER));

            IdentityVerificationResult result = gateway.find("idv01A").orElseThrow();

            assertThat(result.status()).isEqualTo(IdentityVerificationResult.Status.VERIFIED);
            assertThat(result.id()).isEqualTo("idv01A");
            assertThat(result.pgTxId()).isEqualTo("pgtx-1");
            assertThat(result.verifiedAt()).isEqualTo(LocalDateTime.of(2026, 10, 24, 10, 3, 12));
            IdentityVerificationResult.VerifiedCustomer c = result.customer();
            assertThat(c.name()).isEqualTo("홍길동");
            assertThat(c.birthDate()).isEqualTo("1998-04-12");
            assertThat(c.gender()).isEqualTo("FEMALE");
            assertThat(c.phoneNumber()).isEqualTo("01012345678");
            assertThat(c.operator()).isEqualTo("LGU_MVNO");
            assertThat(c.foreigner()).isFalse();
            assertThat(c.ci()).isEqualTo("CI-VALUE==");
            assertThat(c.di()).isEqualTo("DI-VALUE==");
        }

        @Test
        @DisplayName("GW-11 VERIFIED · KCP 가 CI 를 안 줌(가맹점 설정 OFF) — ci 만 null · 나머지 정상")
        void verifiedWithoutCi() {
            respond(200, verifiedJson("""
                    {"name":"홍길동","operator":"SKT","phoneNumber":"01012345678","birthDate":"1998-04-12","gender":"MALE",
                     "isForeigner":true,"di":"DI-VALUE=="}"""));

            IdentityVerificationResult.VerifiedCustomer c = gateway.find("idv01A").orElseThrow().customer();

            assertThat(c.ci()).isNull();
            assertThat(c.di()).isEqualTo("DI-VALUE==");
            assertThat(c.gender()).isEqualTo("MALE");
            assertThat(c.foreigner()).isTrue();
        }

        @Test
        @DisplayName("GW-12 VERIFIED · 모르는 필드가 섞여도 통과(SDK ignoreUnknownKeys) — 포트원이 필드를 더해도 안 깨진다")
        void unknownFieldsAreIgnored() {
            respond(200, verifiedJson(FULL_CUSTOMER.replaceFirst("\\{", "{\"newCustomerField\":\"x\","))
                    .replaceFirst("\\{", "{\"brandNewField\":{\"x\":1},"));

            IdentityVerificationResult result = gateway.find("idv01A").orElseThrow();

            assertThat(result.isVerified()).isTrue();
            assertThat(result.customer().di()).isEqualTo("DI-VALUE==");
        }

        @Test
        @DisplayName("GW-13 전화번호에 하이픈이 섞여 와도 숫자만 남긴다(문서상 숫자만이지만 방어)")
        void phoneDigitsOnly() {
            respond(200, verifiedJson(FULL_CUSTOMER.replace("01012345678", "010-1234-5678")));

            assertThat(gateway.find("idv01A").orElseThrow().customer().phoneNumber()).isEqualTo("01012345678");
        }

        @Test
        @DisplayName("GW-14 FAILED — failure{reason · pgCode · pgMessage} · pgTxId(선택)")
        void failed() {
            respond(200, """
                    {"status":"FAILED","id":"idv01A","channel":%s,"requestedCustomer":%s,
                     "requestedAt":"2026-10-24T01:00:00Z","updatedAt":"2026-10-24T01:01:00Z","statusChangedAt":"2026-10-24T01:01:00Z",
                     "failure":{"reason":"PG_PROVIDER","pgCode":"CS24","pgMessage":"정보조회가 올바르지 않습니다."},
                     "version":"V2","pgTxId":"pgtx-2"}
                    """.formatted(CHANNEL_JSON, REQUESTED_CUSTOMER));

            IdentityVerificationResult result = gateway.find("idv01A").orElseThrow();

            assertThat(result.status()).isEqualTo(IdentityVerificationResult.Status.FAILED);
            assertThat(result.customer()).isNull();
            assertThat(result.failure().reason()).isEqualTo("PG_PROVIDER");
            assertThat(result.failure().pgCode()).isEqualTo("CS24");
            assertThat(result.failure().pgMessage()).isEqualTo("정보조회가 올바르지 않습니다.");
            assertThat(result.pgTxId()).isEqualTo("pgtx-2");
        }

        @Test
        @DisplayName("GW-15 FAILED · pgTxId 없음 — null")
        void failedWithoutPgTxId() {
            respond(200, """
                    {"status":"FAILED","id":"idv01A","requestedCustomer":{},
                     "requestedAt":"2026-10-24T01:00:00Z","updatedAt":"2026-10-24T01:01:00Z","statusChangedAt":"2026-10-24T01:01:00Z",
                     "failure":{},"version":"V2"}
                    """);

            IdentityVerificationResult result = gateway.find("idv01A").orElseThrow();

            assertThat(result.isFailed()).isTrue();
            assertThat(result.pgTxId()).isNull();
            assertThat(result.failure().pgCode()).isNull();
        }

        @Test
        @DisplayName("GW-16 READY — 창을 열었지만 끝내지 않음")
        void ready() {
            respond(200, """
                    {"status":"READY","id":"idv01A","channel":%s,"requestedCustomer":%s,
                     "requestedAt":"2026-10-24T01:00:00Z","updatedAt":"2026-10-24T01:00:00Z","statusChangedAt":"2026-10-24T01:00:00Z",
                     "version":"V2"}
                    """.formatted(CHANNEL_JSON, REQUESTED_CUSTOMER));

            IdentityVerificationResult result = gateway.find("idv01A").orElseThrow();

            assertThat(result.status()).isEqualTo(IdentityVerificationResult.Status.READY);
            assertThat(result.customer()).isNull();
            assertThat(result.failure()).isNull();
        }

        @Test
        @DisplayName("GW-17 모르는 status(SDK Unrecognized) — 결과 모름(PaymentGatewayException)")
        void unknownStatus() {
            respond(200, """
                    {"status":"SOMETHING_NEW","id":"idv01A","requestedAt":"2026-10-24T01:00:00Z"}""");

            assertThatThrownBy(() -> gateway.find("idv01A")).isInstanceOf(PaymentGatewayException.class)
                    .isNotInstanceOf(PaymentGatewayRejectedException.class);
        }

        @Test
        @DisplayName("GW-18 200 인데 JSON 이 깨짐 · VERIFIED 필수 필드(pgRawResponse) 누락 — 결과 모름")
        void malformedBody() {
            respond(200, "{not-json");
            assertThatThrownBy(() -> gateway.find("idv01A")).isInstanceOf(PaymentGatewayException.class);

            respond(200, verifiedJson(FULL_CUSTOMER).replace(",\"pgRawResponse\":\"{\\\"raw\\\":true}\"", ""));
            assertThatThrownBy(() -> gateway.find("idv01A")).isInstanceOf(PaymentGatewayException.class);
        }
    }

    // ================================================================== 오류 응답

    @Nested
    @DisplayName("오류 응답 → 예외")
    class ErrorResponse {

        @Test
        @DisplayName("GW-20 404 IDENTITY_VERIFICATION_NOT_FOUND — Optional.empty()(창을 열기 전 완료 호출 · 정상 경로)")
        void notFoundIsEmpty() {
            respond(404, "{\"type\":\"IDENTITY_VERIFICATION_NOT_FOUND\",\"message\":\"본인인증 건이 존재하지 않습니다.\"}");

            Optional<IdentityVerificationResult> result = gateway.find("idv01A");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("GW-21 401 UNAUTHORIZED — 거절(PaymentGatewayRejectedException) · type = Unauthorized · cause 보존")
        void unauthorized() {
            respond(401, "{\"type\":\"UNAUTHORIZED\",\"message\":\"인증 정보가 올바르지 않습니다.\"}");

            assertThatThrownBy(() -> gateway.find("idv01A"))
                    .isInstanceOfSatisfying(PaymentGatewayRejectedException.class, e -> {
                        assertThat(e.getType()).isEqualTo("Unauthorized");
                        assertThat(e.getCause()).isInstanceOf(UnauthorizedException.class);
                    });
        }

        @Test
        @DisplayName("GW-22 403 FORBIDDEN · 400 INVALID_REQUEST — 거절")
        void forbiddenAndInvalid() {
            respond(403, "{\"type\":\"FORBIDDEN\",\"message\":\"권한 없음\"}");
            assertThatThrownBy(() -> gateway.find("idv01A"))
                    .isInstanceOfSatisfying(PaymentGatewayRejectedException.class,
                            e -> assertThat(e.getCause()).isInstanceOf(ForbiddenException.class));

            respond(400, "{\"type\":\"INVALID_REQUEST\",\"message\":\"잘못된 요청\"}");
            assertThatThrownBy(() -> gateway.find("idv01A"))
                    .isInstanceOfSatisfying(PaymentGatewayRejectedException.class,
                            e -> assertThat(e.getCause()).isInstanceOf(InvalidRequestException.class));
        }

        @Test
        @DisplayName("GW-23 5xx · 모르는 오류 type · HTML 본문 — 결과 모름(거절이 아니다 — 재시도할 수 있다)")
        void serverErrorIsUnknown() {
            respond(500, "<html>Internal Server Error</html>");
            assertThatThrownBy(() -> gateway.find("idv01A")).isInstanceOf(PaymentGatewayException.class)
                    .isNotInstanceOf(PaymentGatewayRejectedException.class);

            respond(503, "{\"type\":\"SOMETHING_UNKNOWN\",\"message\":\"x\"}");
            assertThatThrownBy(() -> gateway.find("idv01A")).isInstanceOf(PaymentGatewayException.class)
                    .isNotInstanceOf(PaymentGatewayRejectedException.class);
        }
    }

    // ================================================================== 통신 실패

    @Nested
    @DisplayName("통신 실패")
    class Transport {

        @Test
        @DisplayName("GW-30 응답이 읽기 + 연결 타임아웃(설정)을 넘김 — 「타임아웃」 PaymentGatewayException · SDK 60초를 기다리지 않는다")
        void timeout() {
            responder = exchange -> {
                try {
                    Thread.sleep(3_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            };
            long started = System.nanoTime();

            assertThatThrownBy(() -> gateway.find("idv01A"))
                    .isInstanceOf(PaymentGatewayException.class).hasMessageContaining("타임아웃");
            assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_500);
        }

        @Test
        @DisplayName("GW-31 서버가 내려가 연결 거부 — 결과 모름(PaymentGatewayException)")
        void connectionRefused() {
            stopServerOnce();

            assertThatThrownBy(() -> gateway.find("idv01A")).isInstanceOf(PaymentGatewayException.class)
                    .isNotInstanceOf(PaymentGatewayRejectedException.class);
        }
    }

    // ================================================================== 기동 검증

    @Nested
    @DisplayName("기동 검증 (2-2)")
    class Startup {

        @Test
        @DisplayName("GW-40 storeId · apiSecret · channel-keys.identity 중 하나라도 비면 기동 실패 — 키 이름이 메시지에")
        void requiresConfig() {
            PortOneProperties noStore = properties(500);
            noStore.setStoreId(" ");
            assertThatThrownBy(() -> new PortOneIdentityV2Gateway(noStore))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("portone.store-id");

            PortOneProperties noSecret = properties(500);
            noSecret.setApiSecret(null);
            assertThatThrownBy(() -> new PortOneIdentityV2Gateway(noSecret))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("portone.api-secret");

            PortOneProperties noChannel = properties(500);
            noChannel.getChannelKeys().setIdentity("");
            assertThatThrownBy(() -> new PortOneIdentityV2Gateway(noChannel))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("portone.channel-keys.identity")
                    .hasMessageContaining("portone.identity.enabled=false");
        }

        @Test
        @DisplayName("GW-41 결제 채널 키(card)는 보지 않는다 — 본인인증만 켤 수 있다")
        void doesNotRequirePaymentChannel() {
            PortOneProperties p = properties(500);
            p.getChannelKeys().setCard(null);
            p.setWebhookSecret(null);

            PortOneIdentityV2Gateway onlyIdentity = new PortOneIdentityV2Gateway(p);
            onlyIdentity.close();
        }
    }
}
