package showroomz.global.payment.settlement.inicis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import showroomz.domain.settlement.port.SettlementPayoutGateway;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupCommand;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupLookup;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.GroupSubmitResult;
import showroomz.domain.settlement.port.SettlementPayoutGateway.Outcome;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.InicisPayoutsProperties;
import showroomz.global.payment.portone.PaymentGatewayException;
import showroomz.global.utils.BusinessCalendar;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 이니시스 지급대행 어댑터(60 전환 계획서 9-1) — HTTP 는 {@code MockRestServiceServer}. 합계 INS · 0원 DEL · dt-pay-basis · 같은 날짜 묶음 여럿 →
 * payresult 1회 · 금액 불일치 그대로 전달 · 통신 실패 → 예외(결과 모름) · 잔액 · 스테이징 assume-paid.
 */
@DisplayName("이니시스 지급대행 어댑터 — 합계 INS · DEL · dtPay · 송금 결과 날짜별 1회 · 결과 모름 · 잔액 · assume-paid")
class InicisSettlementPayoutGatewayTest {

    private static final String BASE = "https://apiwi.inicis.com/rest/api/openmall";
    /** 2026-11-16 월요일 · 2026-11-13 금요일. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusinessCalendar calendar = new BusinessCalendar(new String[0]);
    private InicisPayoutsProperties properties;
    private MockRestServiceServer server;
    private InicisPayoutsClient client;
    private InicisSettlementPayoutGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new InicisPayoutsProperties();
        properties.setMerchantId("INIpayTest");
        properties.setApiKey("api-key");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InicisPayoutsClient(properties, builder, objectMapper);
        gateway = new InicisSettlementPayoutGateway(client, false, properties, calendar);
    }

    @Test
    @DisplayName("묶음 경로 상수 — 리드 1영업일 · 마감 15:00 · NEXT_DAY · INICIS")
    void shape() {
        assertThat(gateway.leadBusinessDays()).isEqualTo(1);
        assertThat(gateway.uploadCutoff()).contains(LocalTime.of(15, 0));
        assertThat(gateway.scheduleType()).isEqualTo("NEXT_DAY");
        assertThat(gateway.provider()).isEqualTo("INICIS");
    }

    @Test
    @DisplayName("합계 INS — POST /payreq · 헤더 2종 · idMall · dtPay(지급일) · amtSupply 합계 · clService 0 · INS · REQ · SB01 → REQUESTED + X-Request-ID")
    void submitInsert() throws Exception {
        String[] requestId = new String[1];
        server.expect(requestTo(BASE + "/payreq")).andExpect(method(HttpMethod.POST))
                .andExpect(header(InicisPayoutsClient.API_KEY_HEADER, "api-key"))
                .andExpect(request -> {
                    requestId[0] = request.getHeaders().getFirst(InicisPayoutsClient.REQUEST_ID_HEADER);
                    JsonNode body = body(request);
                    assertThat(body.path("idMerchant").asText()).isEqualTo("INIpayTest");
                    assertThat(body.path("idMall").asText()).isEqualTo("b12");
                    assertThat(body.path("dtPay").asText()).isEqualTo("20261116");
                    assertThat(body.path("amtSupply").asText()).isEqualTo("35180");
                    assertThat(body.path("clService").asText()).isEqualTo("0");
                    assertThat(body.path("svcDmlCd").asText()).isEqualTo("INS");
                    assertThat(body.path("svcType").asText()).isEqualTo("REQ");
                    assertThat(body.has("noAcct")).isFalse();   // 계좌는 보내지 않는다(서브몰 등록 계좌로 나간다)
                })
                .andRespond(withSuccess("{\"code\":\"SB01\",\"msg\":\"정상\"}", MediaType.APPLICATION_JSON));

        GroupSubmitResult result = gateway.submitGroup(command(35_180));

        assertThat(result.outcome()).isEqualTo(Outcome.REQUESTED);
        assertThat(result.requestId()).isNotBlank().isEqualTo(requestId[0]);
        server.verify();
    }

    @Test
    @DisplayName("합계 0 → DEL(성공 SB03) · dt-pay-basis UPLOAD_DATE 면 dtPay 만 전 영업일(월요일 지급 → 금요일)")
    void deleteAndUploadDateBasis() throws Exception {
        properties.setDtPayBasis(InicisPayoutsProperties.DtPayBasis.UPLOAD_DATE);
        server.expect(requestTo(BASE + "/payreq")).andExpect(request -> {
                    JsonNode body = body(request);
                    assertThat(body.path("svcDmlCd").asText()).isEqualTo("DEL");
                    assertThat(body.path("amtSupply").asText()).isEqualTo("0");
                    assertThat(body.path("dtPay").asText()).isEqualTo("20261113");
                })
                .andRespond(withSuccess("{\"code\":\"SB03\",\"msg\":\"지급요청 데이터 삭제 성공 하였습니다.\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.submitGroup(command(0)).outcome()).isEqualTo(Outcome.REQUESTED);
        server.verify();
    }

    @Test
    @DisplayName("SB05(고객확인 대상 · 등록 성공) → REQUESTED + AML 안내 · EM17(서브몰 정보) → FAILED(PARTNER_NOT_READY) · EM14(지급일 오류) → FAILED(REJECTED_BY_PG · 원문 보존) · 5xx → 예외(결과 모름)")
    void rejectionsAndUnknownOutcome() {
        server.expect(requestTo(BASE + "/payreq")).andRespond(withSuccess(
                "{\"code\":\"SB05\",\"msg\":\"고객확인이행 대상입니다. 지급요청 데이터 등록은 성공하였습니다.\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/payreq"))
                .andRespond(withSuccess("{\"code\":\"EM17\",\"msg\":\"서브몰 정보 유효성 검사\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/payreq"))
                .andRespond(withSuccess("{\"code\":\"EM14\",\"msg\":\"지급일 오류 발생\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/payreq")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        GroupSubmitResult aml = gateway.submitGroup(command(1_000));
        assertThat(aml.outcome()).isEqualTo(Outcome.REQUESTED);
        assertThat(aml.failCode()).isEqualTo("AML_REQUIRED");
        assertThat(aml.requestId()).isNotBlank();
        assertThat(gateway.submitGroup(command(1_000)).failCode()).isEqualTo("PARTNER_NOT_READY");
        GroupSubmitResult failed = gateway.submitGroup(command(1_000));
        assertThat(failed.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(failed.failCode()).isEqualTo("REJECTED_BY_PG");
        assertThat(failed.failReason()).contains("EM14");
        assertThatThrownBy(() -> gateway.submitGroup(command(1_000))).isInstanceOf(PaymentGatewayException.class);
        server.verify();
    }

    @Test
    @DisplayName("송금 결과 — 같은 지급일 묶음 셋 → GET /payresult 1회(지급일 범위 · 익일지급 · 지급) · 01 → PAID(remitDtPay · 금액) · 12 → FAILED(INICIS_12) · 결과 없음 → 돌려주지 않음 · 금액 불일치는 그대로")
    void lookupByDate() {
        server.expect(ExpectedCount.once(), requestTo(startsWith(BASE + "/payresult"))).andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("idMerchant", "INIpayTest"))
                .andExpect(queryParam("startDtPay", "20261116")).andExpect(queryParam("endDtPay", "20261116"))
                .andExpect(queryParam("clService", "0")).andExpect(queryParam("svcType", "REQ"))
                .andRespond(withSuccess("""
                        {"code":"SC01","msg":"정상","data":[
                          {"idMall":"b12","dtPay":"20261116","amtSupply":35180,"remitDtPay":"20261116","clPay":"01"},
                          {"idMall":"c7","dtPay":"20261116","amtSupply":9000,"remitDtPay":"20261116","clPay":"12"}
                        ]}""", MediaType.APPLICATION_JSON));

        List<GroupResult> results = gateway.lookupGroups(List.of(
                new GroupLookup(1L, SettlementPayee.BRAND, "b12", MONDAY, "NEXT_DAY", 30_000L),
                new GroupLookup(2L, SettlementPayee.CREATOR, "c7", MONDAY, "NEXT_DAY", 9_000L),
                new GroupLookup(3L, SettlementPayee.BRAND, "b99", MONDAY, "NEXT_DAY", 1_000L)));

        assertThat(results).extracting(GroupResult::groupId, GroupResult::outcome, GroupResult::failCode, GroupResult::pgAmount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, Outcome.PAID, null, 35_180L),
                        org.assertj.core.groups.Tuple.tuple(2L, Outcome.FAILED, "INICIS_12", 9_000L));
        assertThat(results.get(0).remittedOn()).isEqualTo(MONDAY);
        server.verify();
    }

    @Test
    @DisplayName("일시 오류 — EM18(지급 등록 실패) · EH06(요청 id 중복) → RETRY(원문 코드 · 실패로 돌리지 않는다) · DEL 의 EM08(삭제 실패)도 RETRY")
    void transientErrorsRetry() {
        server.expect(requestTo(BASE + "/payreq"))
                .andRespond(withSuccess("{\"code\":\"EM18\",\"msg\":\"지급요청 데이터 등록 실패 하였습니다.\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/payreq"))
                .andRespond(withSuccess("{\"code\":\"EH06\",\"msg\":\"X-Request-ID는 중복 접수 불가능 합니다.\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/payreq"))
                .andRespond(withSuccess("{\"code\":\"EM08\",\"msg\":\"지급요청 데이터 삭제 실패 하였습니다.\"}", MediaType.APPLICATION_JSON));

        GroupSubmitResult insert = gateway.submitGroup(command(1_000));
        assertThat(insert.outcome()).isEqualTo(Outcome.RETRY);
        assertThat(insert.failCode()).isEqualTo("EM18");
        assertThat(gateway.submitGroup(command(1_000)).outcome()).isEqualTo(Outcome.RETRY);
        GroupSubmitResult delete = gateway.submitGroup(command(0));
        assertThat(delete.outcome()).isEqualTo(Outcome.RETRY);
        assertThat(delete.failCode()).isEqualTo("EM08");
        server.verify();
    }

    @Test
    @DisplayName("송금 결과의 계좌 — cdBank · noAcct 끝 4자리를 싣는다(마스킹 문자는 건너뛴다) · 한 키에 계좌가 갈리면 싣지 않는다 · 계좌가 없으면 null")
    void lookupCarriesRemittedAccount() {
        server.expect(requestTo(startsWith(BASE + "/payresult"))).andRespond(withSuccess("""
                {"code":"SC01","msg":"정상","data":[
                  {"idMall":"b12","dtPay":"20261116","amtSupply":30000,"remitDtPay":"20261116","clPay":"01","cdBank":"088","noAcct":"110****6789"},
                  {"idMall":"c7","dtPay":"20261116","amtSupply":5000,"remitDtPay":"20261116","clPay":"01","cdBank":"004","noAcct":"1234"},
                  {"idMall":"c7","dtPay":"20261116","amtSupply":4000,"remitDtPay":"20261116","clPay":"01","cdBank":"004","noAcct":"9999"},
                  {"idMall":"b99","dtPay":"20261116","amtSupply":1000,"remitDtPay":"20261116","clPay":"01"}
                ]}""", MediaType.APPLICATION_JSON));

        List<GroupResult> results = gateway.lookupGroups(List.of(
                new GroupLookup(1L, SettlementPayee.BRAND, "b12", MONDAY, "NEXT_DAY", 30_000L),
                new GroupLookup(2L, SettlementPayee.CREATOR, "c7", MONDAY, "NEXT_DAY", 9_000L),
                new GroupLookup(3L, SettlementPayee.BRAND, "b99", MONDAY, "NEXT_DAY", 1_000L)));

        assertThat(results.get(0).remittedTo()).isEqualTo(new SettlementPayoutGateway.RemittedAccount("088", "6789"));
        assertThat(results.get(1).remittedTo()).isNull();
        assertThat(results.get(2).remittedTo()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("한 키에 결과가 여럿 — 성공 · 실패가 섞이면 성공 합계만 PAID(묶음 전체를 FAILED 로 닫지 않는다 · 재분배 이중 지급 방지) · 전부 실패일 때만 FAILED")
    void mixedResultsCountOnlyWhatWasSent() {
        server.expect(requestTo(startsWith(BASE + "/payresult"))).andRespond(withSuccess("""
                {"code":"SC01","msg":"정상","data":[
                  {"idMall":"b12","dtPay":"20261116","amtSupply":30000,"remitDtPay":"20261116","clPay":"01"},
                  {"idMall":"b12","dtPay":"20261116","amtSupply":20000,"remitDtPay":"20261116","clPay":"12"},
                  {"idMall":"c7","dtPay":"20261116","amtSupply":5000,"remitDtPay":"20261116","clPay":"12"},
                  {"idMall":"c7","dtPay":"20261116","amtSupply":4000,"remitDtPay":"20261116","clPay":"13"}
                ]}""", MediaType.APPLICATION_JSON));

        List<GroupResult> results = gateway.lookupGroups(List.of(
                new GroupLookup(1L, SettlementPayee.BRAND, "b12", MONDAY, "NEXT_DAY", 50_000L),
                new GroupLookup(2L, SettlementPayee.CREATOR, "c7", MONDAY, "NEXT_DAY", 9_000L)));

        assertThat(results).extracting(GroupResult::groupId, GroupResult::outcome, GroupResult::failCode, GroupResult::pgAmount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, Outcome.PAID, null, 30_000L),
                        org.assertj.core.groups.Tuple.tuple(2L, Outcome.FAILED, "INICIS_12", 9_000L));
        server.verify();
    }

    @Test
    @DisplayName("송금 결과 SC02(조회 데이터 없음) — 오류가 아니라 빈 결과 · 묶음은 그대로 OPEN")
    void lookupEmpty() {
        server.expect(requestTo(startsWith(BASE + "/payresult")))
                .andRespond(withSuccess("{\"code\":\"SC02\",\"msg\":\"송금결과 내역 조회 데이터가 없습니다.\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.lookupGroups(List.of(new GroupLookup(1L, SettlementPayee.BRAND, "b12", MONDAY, "NEXT_DAY", 1_000L))))
                .isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("X-Request-ID — 호출마다 다른 값 · 매뉴얼 샘플 꼴(UUID-yyyyMMddHHmmssS · 54자 안팎)")
    void requestIdShape() {
        String id = InicisPayoutsClient.requestId();
        assertThat(id).matches("^[0-9a-f-]{36}-\\d{15,17}$").isNotEqualTo(InicisPayoutsClient.requestId());
    }

    @Test
    @DisplayName("잔액 — GET /paycheck totalAmt · 스테이징(PG_TEST)은 조회 불가라 empty · assume-paid 면 지급일이 된 묶음을 확정 합계 그대로 PAID")
    void balanceAndStaging() {
        server.expect(requestTo(startsWith(BASE + "/paycheck")))
                .andRespond(withSuccess("{\"code\":\"SD01\",\"msg\":\"정상\",\"data\":{\"totalAmt\":\"1500000\"}}",
                        MediaType.APPLICATION_JSON));
        assertThat(gateway.availableBalance()).contains(1_500_000L);
        server.verify();

        InicisSettlementPayoutGateway staging = new InicisSettlementPayoutGateway(client, true, properties, calendar);
        assertThat(staging.availableBalance()).isEmpty();
        GroupLookup due = new GroupLookup(1L, SettlementPayee.BRAND, "tb12", LocalDate.now().minusDays(1), "NEXT_DAY", 7_000L);
        GroupLookup future = new GroupLookup(2L, SettlementPayee.BRAND, "tb12", LocalDate.now().plusDays(3), "NEXT_DAY", 7_000L);
        assertThat(staging.lookupGroups(List.of(due, future))).isEmpty();
        properties.getTest().setAssumePaid(true);
        assertThat(staging.lookupGroups(List.of(due, future))).singleElement()
                .satisfies(r -> {
                    assertThat(r.groupId()).isEqualTo(1L);
                    assertThat(r.outcome()).isEqualTo(Outcome.PAID);
                    assertThat(r.pgAmount()).isEqualTo(7_000L);
                });
    }

    // ------------------------------------------------------------------ 보조

    private JsonNode body(org.springframework.http.client.ClientHttpRequest request) throws java.io.IOException {
        return objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8));
    }

    private static GroupCommand command(long total) {
        return new GroupCommand(1L, SettlementPayee.BRAND, 12L, "b12", MONDAY, "NEXT_DAY", total);
    }
}
