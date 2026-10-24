package showroomz.global.payment.settlement.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Account;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Business;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Contact;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerProfile;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerResult;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerStatus;
import showroomz.domain.settlement.port.SettlementPayoutGateway.LineResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.Outcome;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutCommand;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutLine;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutLookup;
import showroomz.domain.settlement.port.SettlementPayoutGateway.PayoutResult;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.TossPayoutsProperties;
import showroomz.global.utils.BusinessCalendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스 어댑터 · 클라이언트(토스 설계서 5-2 · 5-4 · 6-2) — HTTP 는 {@code MockRestServiceServer}. 요청 본문은 JWE 라 복호화해서 확인하고,
 * 응답은 같은 키로 암호화해 준다(암호화 호출은 2xx · 4xx 모두 JWE).
 */
@DisplayName("토스 지급대행 어댑터 — 요청 · 멱등 · 유형 · 거절 · 결과 매핑")
class TossSettlementPayoutGatewayTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    /** 2026-10-15 목요일 · 2026-10-16 금요일. */
    private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 15);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TossJwe jwe = new TossJwe(KEY);
    private final BusinessCalendar calendar = new BusinessCalendar(new String[0]);
    private TossPayoutsProperties properties;
    private MockRestServiceServer server;
    private TossSettlementPayoutGateway gateway;
    private TossSellerRegistrar registrar;

    @BeforeEach
    void setUp() {
        properties = new TossPayoutsProperties();
        properties.setSecretKey("test_sk_abc");
        properties.setSecurityKey(KEY);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TossPayoutsClient client = new TossPayoutsClient(properties, builder, objectMapper);
        gateway = new TossSettlementPayoutGateway(client, true, properties, calendar, objectMapper);
        registrar = new TossSellerRegistrar(client, true, properties);
    }

    // ------------------------------------------------------------------ 지시(5-2)

    @Test
    @DisplayName("지시 — 셀러 계좌 같으면(토스 응답은 마스킹 계좌번호) 수정 없이 POST /v2/payouts · 배열 1건 · JWE · Idempotency-Key = UUIDv3(ref) · EXPRESS · amount{KRW} · 적요 · REQUESTED + id")
    void requestExpress() throws Exception {
        String ref = "t-STL-2610-006-BRAND-0";
        // 토스는 계좌번호를 마스킹해 돌려준다(가이드 응답 예 123*****90123) — 그대로 같은 계좌로 봐야 수정 호출이 나가지 않는다.
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-1")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic dGVzdF9za19hYmM6"))
                .andRespond(withSuccess(sellerEnvelope("SELLER-1", "PARTIALLY_APPROVED", "088", "110*****6789"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts")).andExpect(method(HttpMethod.POST))
                .andExpect(header(TossPayoutsClient.SECURITY_MODE_HEADER, "ENCRYPTION"))
                .andExpect(header(TossPayoutsClient.IDEMPOTENCY_HEADER, SettlementPartnerIds.idempotencyKey(ref)))
                .andExpect(request -> {
                    JsonNode body = objectMapper.readTree(jwe.decrypt(request.getBody().toString()));
                    assertThat(body.isArray()).isTrue();
                    JsonNode item = body.get(0);
                    assertThat(item.path("refPayoutId").asText()).isEqualTo(ref);
                    assertThat(item.path("destination").asText()).isEqualTo("SELLER-1");
                    assertThat(item.path("scheduleType").asText()).isEqualTo("EXPRESS");
                    assertThat(item.has("payoutDate")).isFalse();
                    assertThat(item.path("amount").path("currency").asText()).isEqualTo("KRW");
                    assertThat(item.path("amount").path("value").asLong()).isEqualTo(35_180L);
                    assertThat(item.path("transactionDescription").asText()).isEqualTo("쇼룸즈정산");
                    assertThat(item.path("metadata").path("payee").asText()).isEqualTo("BRAND");
                })
                .andRespond(withSuccess(jwe.encrypt(payoutList("PO-1", ref, "REQUESTED", "EXPRESS", "2026-10-15", 35_180L, null)),
                        MediaType.TEXT_PLAIN));

        PayoutResult result = gateway.distribute(command(THURSDAY.atTime(10, 0), line(SettlementPayee.BRAND, 35_180L, "SELLER-1")));

        server.verify();
        assertThat(result.lines()).hasSize(2);
        LineResult brand = result.lines().get(0);
        assertThat(brand.outcome()).isEqualTo(Outcome.REQUESTED);
        assertThat(brand.pgReference()).isEqualTo("PO-1");
        assertThat(brand.pgTransferId()).isEqualTo(ref);
        assertThat(brand.scheduleType()).isEqualTo("EXPRESS");
        assertThat(brand.payoutDate()).isEqualTo(THURSDAY);
        assertThat(result.lines().get(1).outcome()).isEqualTo(Outcome.PAID);   // 플랫폼 몫
    }

    @Test
    @DisplayName("유형 — 영업일 08:00 ~ 15:00 안은 EXPRESS · 07:59 · 15:00 · 토요일은 SCHEDULED(다음 영업일)")
    void scheduleWindow() {
        assertThat(gateway.scheduleFor(THURSDAY.atTime(8, 0)).type()).isEqualTo("EXPRESS");
        assertThat(gateway.scheduleFor(THURSDAY.atTime(14, 59)).type()).isEqualTo("EXPRESS");
        assertThat(gateway.scheduleFor(THURSDAY.atTime(7, 59)))
                .isEqualTo(new TossSettlementPayoutGateway.Schedule("SCHEDULED", THURSDAY.plusDays(1)));
        assertThat(gateway.scheduleFor(THURSDAY.atTime(15, 0)).type()).isEqualTo("SCHEDULED");
        // 토요일 → 월요일
        assertThat(gateway.scheduleFor(LocalDate.of(2026, 10, 17).atTime(10, 0)))
                .isEqualTo(new TossSettlementPayoutGateway.Schedule("SCHEDULED", LocalDate.of(2026, 10, 19)));
        // 금요일 16:00 → 월요일
        assertThat(gateway.scheduleFor(LocalDate.of(2026, 10, 16).atTime(16, 0)).payoutDate()).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("지시 — 계좌가 다르면 셀러 수정 뒤 요청 · 창 밖이면 SCHEDULED + payoutDate · 잔액 거절은 DEFERRED · 중복은 REQUESTED(id 없음)")
    void syncAccountAndRejections() throws Exception {
        String ref = "t-STL-2610-006-CREATOR-0";
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-2"))
                .andRespond(withSuccess(seller("SELLER-2", "PARTIALLY_APPROVED", "004", "999"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-2")).andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    JsonNode body = objectMapper.readTree(jwe.decrypt(request.getBody().toString()));
                    assertThat(body.path("account").path("bankCode").asText()).isEqualTo("088");
                    assertThat(body.path("account").path("accountNumber").asText()).isEqualTo("110123456789");
                    assertThat(body.has("company")).isFalse();
                })
                .andRespond(withSuccess(jwe.encrypt(seller("SELLER-2", "PARTIALLY_APPROVED", "088", "110123456789")), MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts"))
                .andExpect(request -> {
                    JsonNode item = objectMapper.readTree(jwe.decrypt(request.getBody().toString())).get(0);
                    assertThat(item.path("scheduleType").asText()).isEqualTo("SCHEDULED");
                    assertThat(item.path("payoutDate").asText()).isEqualTo("2026-10-16");
                })
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN)
                        .body(jwe.encrypt(error("EXCEEDED_PAYOUT_BALANCE_AMOUNT", "잔액 부족"))));

        PayoutResult deferred = gateway.distribute(command(THURSDAY.atTime(16, 0), line(SettlementPayee.CREATOR, 12_000L, "SELLER-2")));
        assertThat(deferred.lines().get(0).outcome()).isEqualTo(Outcome.DEFERRED);
        assertThat(deferred.lines().get(0).failCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(deferred.lines().get(0).pgTransferId()).isEqualTo(ref);

        server.reset();
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-2"))
                .andRespond(withSuccess(seller("SELLER-2", "PARTIALLY_APPROVED", "088", "110123456789"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN)
                        .body(jwe.encrypt(error("DUPLICATE_PAYOUT_REQUEST", "이미 요청"))));
        PayoutResult duplicate = gateway.distribute(command(THURSDAY.atTime(10, 0), line(SettlementPayee.CREATOR, 12_000L, "SELLER-2")));
        assertThat(duplicate.lines().get(0).outcome()).isEqualTo(Outcome.REQUESTED);
        assertThat(duplicate.lines().get(0).pgReference()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("지시 — 본인인증 필요(FORBIDDEN_SELLER_PAYOUT)는 셀러 상태로 KYC/본인인증을 가른 FAILED(서비스가 BLOCKED 로 되돌린다) · 5xx 는 그 행만 REQUESTED(id 없음 · ref 있음) · 셀러 미연결은 PARTNER_NOT_READY")
    void forbiddenAndUnknown() {
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-3"))
                .andRespond(withSuccess(seller("SELLER-3", "KYC_REQUIRED", "088", "110123456789"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN)
                        .body(jwe.encrypt(error("FORBIDDEN_SELLER_PAYOUT", "KYC"))));
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-3"))
                .andRespond(withSuccess(seller("SELLER-3", "KYC_REQUIRED", "088", "110123456789"), MediaType.APPLICATION_JSON));
        PayoutResult forbidden = gateway.distribute(command(THURSDAY.atTime(10, 0), line(SettlementPayee.BRAND, 1_000L, "SELLER-3")));
        assertThat(forbidden.lines().get(0).outcome()).isEqualTo(Outcome.FAILED);
        assertThat(forbidden.lines().get(0).failCode()).isEqualTo("KYC_REQUIRED");

        server.reset();
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-3"))
                .andRespond(withSuccess(seller("SELLER-3", "APPROVED", "088", "110123456789"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_PLAIN)
                        .body(jwe.encrypt(error("COMMON_ERROR", "일시 오류"))));
        PayoutResult unknown = gateway.distribute(command(THURSDAY.atTime(10, 0), line(SettlementPayee.BRAND, 1_000L, "SELLER-3")));
        LineResult unknownLine = unknown.lines().get(0);
        assertThat(unknownLine.outcome()).isEqualTo(Outcome.REQUESTED);
        assertThat(unknownLine.pgReference()).isNull();
        assertThat(unknownLine.pgTransferId()).isEqualTo("t-STL-2610-006-BRAND-0");
        assertThat(unknown.lines().get(1).outcome()).isEqualTo(Outcome.PAID);   // 다른 행(플랫폼)의 결과는 그대로 돌아온다
        server.verify();

        PayoutResult unlinked = gateway.distribute(command(THURSDAY.atTime(10, 0), line(SettlementPayee.BRAND, 1_000L, null)));
        assertThat(unlinked.lines().get(0).outcome()).isEqualTo(Outcome.FAILED);
        assertThat(unlinked.lines().get(0).failCode()).isEqualTo("PARTNER_NOT_READY");
    }

    @Test
    @DisplayName("지시 — 두 행 중 앞 행은 성공 · 뒤 행은 통신 실패 → 앞 행 REQUESTED(id) 유지 · 뒤 행만 REQUESTED(id 없음) · 요청 전 셀러 조회 통신 실패는 DEFERRED(ACCOUNT_SYNC_FAILED)")
    void perLineUnknownOutcome() {
        String brandRef = "t-STL-2610-006-BRAND-0";
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-1"))
                .andRespond(withSuccess(sellerEnvelope("SELLER-1", "APPROVED", "088", "110*****6789"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts"))
                .andRespond(withSuccess(jwe.encrypt(payoutList("PO-1", brandRef, "REQUESTED", "EXPRESS", "2026-10-15", 35_180L, null)),
                        MediaType.TEXT_PLAIN));
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-2"))
                .andRespond(withSuccess(sellerEnvelope("SELLER-2", "APPROVED", "088", "110*****6789"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts"))
                .andRespond(request -> { throw new java.io.IOException("read timeout"); });

        PayoutResult result = gateway.distribute(new PayoutCommand(1L, "STL-2610-006", List.of(
                line(SettlementPayee.BRAND, 35_180L, "SELLER-1"), line(SettlementPayee.CREATOR, 12_000L, "SELLER-2")), THURSDAY.atTime(10, 0)));

        server.verify();
        assertThat(result.lines()).extracting(LineResult::payoutId, LineResult::outcome, LineResult::pgReference, LineResult::pgTransferId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, Outcome.REQUESTED, "PO-1", brandRef),
                        org.assertj.core.groups.Tuple.tuple(2L, Outcome.REQUESTED, null, "t-STL-2610-006-CREATOR-0"));

        server.reset();
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-1"))
                .andRespond(request -> { throw new java.io.IOException("connect timeout"); });
        PayoutResult deferred = gateway.distribute(command(THURSDAY.atTime(10, 0), line(SettlementPayee.BRAND, 35_180L, "SELLER-1")));
        assertThat(deferred.lines().get(0).outcome()).isEqualTo(Outcome.DEFERRED);
        assertThat(deferred.lines().get(0).failCode()).isEqualTo("ACCOUNT_SYNC_FAILED");
        assertThat(deferred.lines().get(0).pgTransferId()).isEqualTo(brandRef);
        server.verify();
    }

    @Test
    @DisplayName("마스킹 계좌번호 비교 — 길이 같고 * 아닌 자리가 모두 같으면 같은 계좌 · 평문 응답도 같음 · 길이 · 숫자 다르면 다른 계좌")
    void maskedAccountNumber() {
        assertThat(TossSettlementPayoutGateway.sameAccountNumber("110*****6789", "110123456789")).isTrue();
        assertThat(TossSettlementPayoutGateway.sameAccountNumber("110*****6789", "110-1234-56789")).isTrue();
        assertThat(TossSettlementPayoutGateway.sameAccountNumber("110123456789", "110123456789")).isTrue();
        assertThat(TossSettlementPayoutGateway.sameAccountNumber("110*****6780", "110123456789")).isFalse();
        assertThat(TossSettlementPayoutGateway.sameAccountNumber("110*****678", "110123456789")).isFalse();
        assertThat(TossSettlementPayoutGateway.sameAccountNumber("************", "110123456789")).isTrue();
        assertThat(TossSettlementPayoutGateway.sameAccountNumber(null, "1")).isFalse();
        assertThat(TossSettlementPayoutGateway.sameAccountNumber("", "1")).isFalse();
    }

    // ------------------------------------------------------------------ 결과(5-4)

    @Test
    @DisplayName("결과 — id 로 조회 · COMPLETED → PAID · FAILED(error) → 분류 · CANCELED → CANCELLED_AT_PG · IN_PROGRESS 유지 · 404 → TRANSFER_NOT_FOUND")
    void lookupById() {
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts/PO-1"))
                .andRespond(withSuccess(payout("PO-1", "R-1", "COMPLETED", "EXPRESS", "2026-10-15", 35_180L, null), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts/PO-2"))
                .andRespond(withSuccess(payout("PO-2", "R-2", "FAILED", "EXPRESS", "2026-10-15", 12_000L, "INVALID_ACCOUNT_INFO"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts/PO-3"))
                .andRespond(withSuccess(payout("PO-3", "R-3", "CANCELED", "SCHEDULED", "2026-10-16", 1_000L, null), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts/PO-4"))
                .andRespond(withSuccess(payout("PO-4", "R-4", "IN_PROGRESS", "EXPRESS", "2026-10-15", 1_000L, null), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts/PO-5"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NOT_FOUND\",\"message\":\"없음\"}"));

        List<LineResult> results = gateway.lookup(List.of(
                lookup(1L, "R-1", "PO-1", 35_180L), lookup(2L, "R-2", "PO-2", 12_000L), lookup(3L, "R-3", "PO-3", 1_000L),
                lookup(4L, "R-4", "PO-4", 1_000L), lookup(5L, "R-5", "PO-5", 1_000L)));

        assertThat(results).extracting(LineResult::payoutId, LineResult::outcome, LineResult::failCode).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1L, Outcome.PAID, null),
                org.assertj.core.groups.Tuple.tuple(2L, Outcome.FAILED, "ACCOUNT_HOLDER_MISMATCH"),
                org.assertj.core.groups.Tuple.tuple(3L, Outcome.FAILED, "CANCELLED_AT_PG"),
                org.assertj.core.groups.Tuple.tuple(4L, Outcome.REQUESTED, null),
                org.assertj.core.groups.Tuple.tuple(5L, Outcome.FAILED, "TRANSFER_NOT_FOUND"));
        assertThat(results.get(0).pgReference()).isEqualTo("PO-1");
        assertThat(results.get(1).failReason()).startsWith("INVALID_ACCOUNT_INFO");
        assertThat(results.get(2).payoutDate()).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    @DisplayName("결과 — id 없음(결과 모름)은 지급일 범위 목록에서 ref 로 복구 · 유예 안이면 조용히 · 유예 지나면 TRANSFER_NOT_FOUND")
    void lookupByRefFromList() {
        LocalDateTime requestedAt = LocalDate.now().minusDays(10).atTime(10, 0);
        LocalDate from = requestedAt.toLocalDate();
        LocalDate to = calendar.addBusinessDays(from, 1);
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts?limit=1000&payoutDateGte=" + from + "&payoutDateLte=" + to))
                .andRespond(withSuccess("{\"hasMore\":false,\"nextCursor\":null,\"items\":["
                        + payout("PO-9", "R-9", "COMPLETED", "EXPRESS", from.toString(), 500L, null) + "]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts?limit=1000&payoutDateGte=" + from + "&payoutDateLte=" + to))
                .andRespond(withSuccess("{\"hasMore\":false,\"items\":[]}", MediaType.APPLICATION_JSON));

        List<LineResult> results = gateway.lookup(List.of(
                new PayoutLookup(9L, 1L, "STL-1", SettlementPayee.BRAND, 500L, 0, "R-9", null, requestedAt),
                new PayoutLookup(10L, 1L, "STL-1", SettlementPayee.CREATOR, 500L, 0, "R-10", null, requestedAt)));

        assertThat(results).extracting(LineResult::payoutId, LineResult::outcome, LineResult::pgReference, LineResult::failCode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(9L, Outcome.PAID, "PO-9", null),
                        org.assertj.core.groups.Tuple.tuple(10L, Outcome.FAILED, null, "TRANSFER_NOT_FOUND"));
    }

    @Test
    @DisplayName("웹훅 entityBody → 폴링과 같은 매핑 · 잔액 조회 · 대사(목록 합계)")
    void webhookBalanceReconcile() {
        LineResult fromWebhook = gateway.fromWebhook(payout("PO-7", "R-7", "COMPLETED", "EXPRESS", "2026-10-15", 700L, null),
                lookup(7L, "R-7", "PO-7", 700L)).orElseThrow();
        assertThat(fromWebhook.outcome()).isEqualTo(Outcome.PAID);
        assertThat(gateway.fromWebhook("not json", lookup(7L, "R-7", "PO-7", 700L))).isEmpty();

        server.expect(requestTo("https://api.tosspayments.com/v2/balances"))
                .andRespond(withSuccess("{\"pendingAmount\":{\"currency\":\"KRW\",\"value\":100},\"availableAmount\":{\"currency\":\"KRW\",\"value\":5000000}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/payouts?limit=1000&payoutDateGte=2026-10-15&payoutDateLte=2026-10-15"))
                .andRespond(withSuccess("{\"entityBody\":{\"hasMore\":false,\"items\":["
                        + payout("A", "R-A", "REQUESTED", "EXPRESS", "2026-10-15", 100L, null) + ","
                        + payout("B", "R-B", "COMPLETED", "EXPRESS", "2026-10-15", 250L, null) + "]}}", MediaType.APPLICATION_JSON));
        assertThat(gateway.availableBalance()).contains(5_000_000L);
        assertThat(gateway.reconcile(THURSDAY)).hasValueSatisfying(r -> {
            assertThat(r.count()).isEqualTo(2);
            assertThat(r.amount()).isEqualTo(350L);
        });
    }

    // ------------------------------------------------------------------ 셀러(4-2)

    @Test
    @DisplayName("셀러 등록 — 법인 판정 → CORPORATE · company · 계좌 · metadata · 멱등 키 · 응답 id · PARTIALLY_APPROVED → 승인 / APPROVAL_REQUIRED → 대기(본인인증)")
    void registerSeller() throws Exception {
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers")).andExpect(method(HttpMethod.POST))
                .andExpect(header(TossPayoutsClient.IDEMPOTENCY_HEADER, SettlementPartnerIds.idempotencyKey("t-brand-12")))
                .andExpect(request -> {
                    JsonNode body = objectMapper.readTree(jwe.decrypt(request.getBody().toString()));
                    assertThat(body.path("refSellerId").asText()).isEqualTo("t-brand-12");
                    assertThat(body.path("businessType").asText()).isEqualTo("CORPORATE");
                    assertThat(body.path("company").path("businessRegistrationNumber").asText()).isEqualTo("1208112345");
                    assertThat(body.path("company").path("phone").asText()).isEqualTo("01012345678");
                    assertThat(body.has("individual")).isFalse();
                    assertThat(body.path("account").path("bankCode").asText()).isEqualTo("088");
                    assertThat(body.path("metadata").path("marketId").asText()).isEqualTo("12");
                })
                .andRespond(withSuccess(jwe.encrypt(seller("SELLER-NEW", "PARTIALLY_APPROVED", "088", "110123456789")), MediaType.TEXT_PLAIN));
        PartnerResult approved = registrar.ensure(brandProfile(null, false));
        assertThat(approved.status()).isEqualTo(PartnerStatus.APPROVED);
        assertThat(approved.externalId()).isEqualTo("SELLER-NEW");
        assertThat(approved.rawStatus()).isEqualTo("PARTIALLY_APPROVED");

        server.reset();
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers"))
                .andExpect(request -> {
                    JsonNode body = objectMapper.readTree(jwe.decrypt(request.getBody().toString()));
                    assertThat(body.path("businessType").asText()).isEqualTo("INDIVIDUAL");
                    assertThat(body.path("individual").path("name").asText()).isEqualTo("김지민");
                })
                .andRespond(withSuccess(jwe.encrypt(seller("SELLER-IND", "APPROVAL_REQUIRED", "004", "123456789012")), MediaType.TEXT_PLAIN));
        PartnerResult pending = registrar.ensure(new PartnerProfile(SettlementPayee.CREATOR, 7L, "t-creator-7", null, "지민쇼룸",
                new Contact("김지민", "010-9999-8888", "jimin@test.com"), new Account("004", "KB국민은행", "123456789012", "김지민"),
                null, null, false));
        assertThat(pending.status()).isEqualTo(PartnerStatus.PENDING);
        assertThat(pending.externalId()).isEqualTo("SELLER-IND");
        assertThat(pending.failCode()).isEqualTo("IDENTITY_VERIFICATION_REQUIRED");
        server.verify();
    }

    @Test
    @DisplayName("셀러 등록 — DUPLICATE_SELLER 는 목록을 훑어 id 복구 뒤 수정 · INVALID_BUSINESS_NUMBER 는 반대 유형으로 1회 재시도 · 사전 검사(미지원 은행 · 계좌 14자 초과)")
    void duplicateAndTypeRetryAndPrecheck() {
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN)
                        .body(jwe.encrypt("{\"code\":\"DUPLICATE_SELLER\",\"message\":\"이미\"}")));
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers?limit=1000"))
                .andRespond(withSuccess("{\"hasMore\":false,\"items\":[" + seller("OTHER", "APPROVED", "004", "1")
                        .replace("\"refSellerId\":\"ref\"", "\"refSellerId\":\"t-brand-99\"") + ","
                        + seller("SELLER-DUP", "APPROVED", "088", "110123456789").replace("\"refSellerId\":\"ref\"", "\"refSellerId\":\"t-brand-12\"")
                        + "]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers/SELLER-DUP")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jwe.encrypt(seller("SELLER-DUP", "APPROVED", "088", "110123456789")), MediaType.TEXT_PLAIN));
        PartnerResult recovered = registrar.ensure(brandProfile(null, false));
        assertThat(recovered.isApproved()).isTrue();
        assertThat(recovered.externalId()).isEqualTo("SELLER-DUP");

        server.reset();
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN)
                        .body(jwe.encrypt(error("INVALID_BUSINESS_NUMBER", "x"))));
        server.expect(requestTo("https://api.tosspayments.com/v2/sellers"))
                .andExpect(request -> assertThat(objectMapper.readTree(jwe.decrypt(request.getBody().toString()))
                        .path("businessType").asText()).isEqualTo("INDIVIDUAL_BUSINESS"))
                .andRespond(withSuccess(jwe.encrypt(seller("SELLER-IB", "PARTIALLY_APPROVED", "088", "110123456789")), MediaType.TEXT_PLAIN));
        assertThat(registrar.ensure(brandProfile(null, false)).externalId()).isEqualTo("SELLER-IB");
        server.verify();

        PartnerResult hsbc = registrar.ensure(new PartnerProfile(SettlementPayee.BRAND, 12L, "t-brand-12", null, "글로우랩",
                new Contact("김대표", "01012345678", "a@b.c"), new Account("054", "HSBC", "1", "글로우랩"),
                new Business("글로우랩", "1208112345", "김대표", null, null, true), null, false));
        assertThat(hsbc.failCode()).isEqualTo("UNSUPPORTED_BANK");
        PartnerResult longAccount = registrar.ensure(new PartnerProfile(SettlementPayee.BRAND, 12L, "t-brand-12", null, "글로우랩",
                new Contact("김대표", "01012345678", "a@b.c"), new Account("088", "신한", "123456789012345", "글로우랩"),
                new Business("글로우랩", "1208112345", "김대표", null, null, true), null, false));
        assertThat(longAccount.failCode()).isEqualTo("ACCOUNT_INVALID");
    }

    // ------------------------------------------------------------------ 보조

    private static PayoutCommand command(LocalDateTime requestedAt, PayoutLine line) {
        return new PayoutCommand(1L, "STL-2610-006", List.of(line,
                new PayoutLine(99L, SettlementPayee.PLATFORM, 0L, "SHOWROOMZ", null, "SHOWROOMZ", null, 0, null, null)), requestedAt);
    }

    private static PayoutLine line(SettlementPayee payee, long amount, String sellerId) {
        return new PayoutLine(payee == SettlementPayee.BRAND ? 1L : 2L, payee, amount, "신한은행", "110123456789", "글로우랩",
                12L, 0, "088", sellerId);
    }

    private static PayoutLookup lookup(Long payoutId, String ref, String pgPayoutId, long amount) {
        return new PayoutLookup(payoutId, 1L, "STL-2610-006", SettlementPayee.BRAND, amount, 0, ref, pgPayoutId,
                LocalDateTime.now());
    }

    private static PartnerProfile brandProfile(String externalId, boolean existing) {
        return new PartnerProfile(SettlementPayee.BRAND, 12L, "t-brand-12", externalId, "글로우랩",
                new Contact("김대표", "010-1234-5678", "tax@glowlab.kr"), new Account("088", "신한은행", "110123456789", "글로우랩"),
                new Business("글로우랩 주식회사", "1208112345", "김대표", "서울", "도소매", true), null, existing);
    }

    /** API v2 응답 봉투 — 단건 객체는 {@code entityBody} 아래(req-res). */
    private static String sellerEnvelope(String id, String status, String bankCode, String accountNumber) {
        return "{\"version\":\"2024-06-01\",\"traceId\":\"trace-1\",\"entityType\":\"seller\",\"entityBody\":"
                + seller(id, status, bankCode, accountNumber) + "}";
    }

    private static String seller(String id, String status, String bankCode, String accountNumber) {
        return ("{\"id\":\"%s\",\"refSellerId\":\"ref\",\"businessType\":\"CORPORATE\",\"status\":\"%s\","
                + "\"account\":{\"bankCode\":\"%s\",\"accountNumber\":\"%s\",\"holderName\":\"글로우랩\"},\"metadata\":null}")
                .formatted(id, status, bankCode, accountNumber);
    }

    private static String payout(String id, String ref, String status, String type, String date, long amount, String errorCode) {
        String error = errorCode == null ? "null" : "{\"code\":\"%s\",\"message\":\"실패\"}".formatted(errorCode);
        return ("{\"id\":\"%s\",\"refPayoutId\":\"%s\",\"destination\":\"S\",\"scheduleType\":\"%s\",\"payoutDate\":\"%s\","
                + "\"amount\":{\"currency\":\"KRW\",\"value\":%d},\"transactionDescription\":\"쇼룸즈정산\","
                + "\"requestedAt\":\"2026-10-15T10:00:00+09:00\",\"status\":\"%s\",\"error\":%s,\"metadata\":null}")
                .formatted(id, ref, type, date, amount, status, error);
    }

    private static String payoutList(String id, String ref, String status, String type, String date, long amount, String errorCode) {
        return "{\"entityBody\":{\"hasMore\":false,\"nextCursor\":null,\"items\":[" + payout(id, ref, status, type, date, amount, errorCode) + "]}}";
    }

    /** API v2 에러 객체 — {@code error} 아래 중첩(req-res). 암호화 호출의 4xx 는 이 꼴이 JWE 로 온다. */
    private static String error(String code, String message) {
        return "{\"version\":\"2024-06-01\",\"traceId\":\"trace-1\",\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}".formatted(code, message);
    }
}
