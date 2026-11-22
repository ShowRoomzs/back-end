package showroomz.global.payment.settlement.inicis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Account;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Business;
import showroomz.domain.settlement.port.SettlementPartnerGateway.Contact;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerProfile;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerResult;
import showroomz.domain.settlement.port.SettlementPartnerGateway.PartnerStatus;
import showroomz.domain.settlement.service.SettlementPartnerIds;
import showroomz.domain.settlement.type.PayoutBlockReason;
import showroomz.domain.settlement.type.SettlementPayee;
import showroomz.global.config.properties.InicisPayoutsProperties;
import showroomz.global.payment.settlement.inicis.InicisPayoutsDtos.AmlStatus;
import showroomz.global.utils.KstDates;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 이니시스 서브몰 등록(60 전환 계획서 9-1) — HTTP 는 {@code MockRestServiceServer}. 본문 모양(사업자 · 비사업자) · ENC · userHash · INS 거절 → UPD
 * 재시도 · 필수값(D-4) · 형식 검사 · 참조 규칙(D-3).
 */
@DisplayName("이니시스 서브몰 등록 — 본문 모양 · ENC · userHash · 이미 있음 → UPD · 생년월일 · 계좌 형식 · idMall 길이")
class InicisSubMallRegistrarTest {

    private static final String UPSERT = "https://apiwi.inicis.com/rest/api/openmall/upsert";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InicisPayoutsProperties properties;
    private MockRestServiceServer server;
    private InicisSubMallRegistrar registrar;

    @BeforeEach
    void setUp() {
        properties = new InicisPayoutsProperties();
        properties.setMerchantId("INIpayTest");
        properties.setApiKey("api-key");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        registrar = new InicisSubMallRegistrar(new InicisPayoutsClient(properties, builder, objectMapper), false, properties);
    }

    @Test
    @DisplayName("브랜드(사업자) INS — 헤더 2종 · idMall b{marketId} · clId 2 · noComp 숫자 · 개인정보 6필드 ENC · bthDt 8자리 · userHash · SA01 → 승인(id = idMall · REGISTERED)")
    void brandInsert() throws Exception {
        server.expect(requestTo(UPSERT)).andExpect(method(HttpMethod.POST))
                .andExpect(header(InicisPayoutsClient.API_KEY_HEADER, "api-key"))
                .andExpect(request -> assertThat(request.getHeaders().getFirst(InicisPayoutsClient.REQUEST_ID_HEADER)).isNotBlank())
                .andExpect(request -> {
                    JsonNode body = body(request);
                    assertThat(body.path("idMerchant").asText()).isEqualTo("INIpayTest");
                    assertThat(body.path("idMall").asText()).isEqualTo("b12");
                    assertThat(body.path("clId").asText()).isEqualTo("2");
                    assertThat(body.path("noComp").asText()).isEqualTo("1234567890");
                    assertThat(body.path("nmComp").asText()).isEqualTo("(주)코코브라운");
                    assertThat(InicisPayoutCrypto.dec(body.path("nmBoss").asText())).isEqualTo("김대표");
                    assertThat(InicisPayoutCrypto.dec(body.path("nmRegist").asText())).isEqualTo("(주)코코브라운");
                    assertThat(body.path("cdBank").asText()).isEqualTo("088");
                    assertThat(InicisPayoutCrypto.dec(body.path("noAcct").asText())).isEqualTo("110123456789");
                    assertThat(InicisPayoutCrypto.dec(body.path("noTel").asText())).isEqualTo("01012345678");
                    assertThat(InicisPayoutCrypto.dec(body.path("noEmail").asText())).isEqualTo("tax@coco.example");
                    assertThat(InicisPayoutCrypto.dec(body.path("bthDt").asText())).isEqualTo("19850314");
                    assertThat(body.path("svcDmlCd").asText()).isEqualTo("INS");
                    assertThat(body.path("svcType").asText()).isEqualTo("REQ");
                    assertThat(body.path("userHash").asText()).isEqualTo(
                            InicisPayoutCrypto.userHash(KstDates.today(), "INIpayTest", "110123456789", "api-key"));
                })
                .andRespond(withSuccess("{\"code\":\"SA01\",\"msg\":\"정상\"}", MediaType.APPLICATION_JSON));

        PartnerResult result = registrar.ensure(brand("1985-03-14"));

        assertThat(result.status()).isEqualTo(PartnerStatus.APPROVED);
        assertThat(result.externalId()).isEqualTo("b12");
        assertThat(result.rawStatus()).isEqualTo(InicisSubMallRegistrar.STATUS_REGISTERED);
        assertThat(registrar.statusOf(result.rawStatus())).isEqualTo(PartnerStatus.APPROVED);
        assertThat(registrar.isLimited(result.rawStatus())).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("비사업자 인플루언서 — noComp 없음 · nmComp = 활동명 · nmBoss = 본인 이름 · 연결이 있으면 UPD")
    void creatorUpdate() throws Exception {
        server.expect(requestTo(UPSERT)).andExpect(request -> {
                    JsonNode body = body(request);
                    assertThat(body.has("noComp")).isFalse();
                    assertThat(body.path("idMall").asText()).isEqualTo("c7");
                    assertThat(body.path("nmComp").asText()).isEqualTo("지민의 쇼룸");
                    assertThat(InicisPayoutCrypto.dec(body.path("nmBoss").asText())).isEqualTo("김지민");
                    assertThat(body.path("svcDmlCd").asText()).isEqualTo("UPD");
                })
                .andRespond(withSuccess("{\"code\":\"SA01\",\"msg\":\"정상\"}", MediaType.APPLICATION_JSON));

        PartnerResult result = registrar.ensure(creator("19970505", true));

        assertThat(result.isApproved()).isTrue();
        assertThat(result.externalId()).isEqualTo("c7");
        server.verify();
    }

    @Test
    @DisplayName("INS 가 「이미 등록」으로 거절 → UPD 로 1회 재시도 → 승인(D-3 · 코드표 Q5 전엔 메시지 낱말)")
    void alreadyRegisteredRetriesWithUpdate() throws Exception {
        server.expect(requestTo(UPSERT)).andExpect(request -> assertThat(body(request).path("svcDmlCd").asText()).isEqualTo("INS"))
                .andRespond(withSuccess("{\"code\":\"EA30\",\"msg\":\"이미 등록된 서브몰입니다\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPSERT)).andExpect(request -> assertThat(body(request).path("svcDmlCd").asText()).isEqualTo("UPD"))
                .andRespond(withSuccess("{\"code\":\"SA01\",\"msg\":\"정상\"}", MediaType.APPLICATION_JSON));

        assertThat(registrar.ensure(creator("19970505", false)).isApproved()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("그 밖의 거절 — 실패(기본 PARTNER_NOT_READY · 원문 코드 보존) · 예금주 낱말이면 ACCOUNT_HOLDER_MISMATCH")
    void rejectedIsFailed() {
        server.expect(requestTo(UPSERT))
                .andRespond(withSuccess("{\"code\":\"EA20\",\"msg\":\"예금주 불일치\"}", MediaType.APPLICATION_JSON));
        PartnerResult holder = registrar.ensure(creator("19970505", false));
        assertThat(holder.status()).isEqualTo(PartnerStatus.FAILED);
        assertThat(holder.failCode()).isEqualTo(PayoutBlockReason.ACCOUNT_HOLDER_MISMATCH.name());
        assertThat(holder.failReason()).contains("EA20");
        server.verify();
    }

    @Test
    @DisplayName("필수값 · 형식 — 생년월일 없음/형식 틀림 → PARTNER_PROFILE_INCOMPLETE · 계좌 21자 · 예금주 허용 밖 문자 → ACCOUNT_INVALID · PG 를 부르지 않는다")
    void precheckBlocksWithoutCall() {
        assertThat(registrar.ensure(brand(null)).failCode()).isEqualTo(PayoutBlockReason.PARTNER_PROFILE_INCOMPLETE.name());
        assertThat(registrar.ensure(brand("850314")).failCode()).isEqualTo(PayoutBlockReason.PARTNER_PROFILE_INCOMPLETE.name());
        assertThat(registrar.ensure(brand("1985-02-30")).failCode()).isEqualTo(PayoutBlockReason.PARTNER_PROFILE_INCOMPLETE.name());
        PartnerProfile longAccount = withAccount(brand("19850314"), new Account("088", "신한은행", "123456789012345678901", "코코브라운"));
        assertThat(registrar.ensure(longAccount).failCode()).isEqualTo(PayoutBlockReason.ACCOUNT_INVALID.name());
        PartnerProfile badHolder = withAccount(brand("19850314"), new Account("088", "신한은행", "110123456789", "코코@브라운"));
        assertThat(registrar.ensure(badHolder).failCode()).isEqualTo(PayoutBlockReason.ACCOUNT_INVALID.name());
        PartnerProfile noPhone = new PartnerProfile(SettlementPayee.CREATOR, 7L, "c7", null, "지민의 쇼룸",
                new Contact("김지민", null, "jimin@example.com"), new Account("004", "KB국민은행", "9876543210", "김지민"), null,
                "19970505", false);
        assertThat(registrar.ensure(noPhone).failCode()).isEqualTo(PayoutBlockReason.PARTNER_PROFILE_INCOMPLETE.name());
        server.verify();   // 호출 0회
    }

    @Test
    @DisplayName("idMall 규칙(D-3) — 운영 b{id} · c{id} · PG_TEST 는 t 접두 · 10자 통과(c999999999) · 11자는 번호 체계 버그로 터진다")
    void subMallIdRule() {
        assertThat(registrar.partnerRef(SettlementPayee.BRAND, 12L)).isEqualTo("b12");
        assertThat(new InicisSubMallRegistrar(null, true, properties).partnerRef(SettlementPayee.CREATOR, 7L)).isEqualTo("tc7");
        assertThat(SettlementPartnerIds.subMallId("", SettlementPayee.CREATOR, 999_999_999L)).isEqualTo("c999999999").hasSize(10);
        assertThatThrownBy(() -> SettlementPartnerIds.subMallId("", SettlementPayee.CREATOR, 9_999_999_999L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SettlementPartnerIds.subMallId("t", SettlementPayee.BRAND, 999_999_999L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AML 원시값 접기 — E{심사}|R{재이행}|X{유효} · 유효 N(만료)만 막는다(잠정 · Q8) · 스테이징은 조회하지 않는다")
    void amlFolding() {
        String valid = InicisSubMallRegistrar.foldAml(new AmlStatus("Y", "N", null, null, "Y", "20261101"));
        String expired = InicisSubMallRegistrar.foldAml(new AmlStatus("Y", "N", null, null, "N", "20251101"));
        assertThat(valid).isEqualTo("EY|RN|XY").hasSizeLessThanOrEqualTo(16);
        assertThat(registrar.isAmlBlocking(valid)).isFalse();
        assertThat(registrar.isAmlBlocking(expired)).isTrue();
        assertThat(registrar.isAmlBlocking(null)).isFalse();
        assertThat(new InicisSubMallRegistrar(null, true, properties).amlStatus("tc7")).isEmpty();
    }

    // ------------------------------------------------------------------ 보조

    private JsonNode body(org.springframework.http.client.ClientHttpRequest request) throws java.io.IOException {
        return objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8));
    }

    private static PartnerProfile brand(String birthdate) {
        return new PartnerProfile(SettlementPayee.BRAND, 12L, "b12", null, "코코브라운",
                new Contact("김대표", "010-1234-5678", "tax@coco.example"),
                new Account("088", "신한은행", "110-123-456789", "(주)코코브라운"),
                new Business("(주)코코브라운", "123-45-67890", "김대표", "서울", "도소매", true), birthdate, false);
    }

    private static PartnerProfile creator(String birthdate, boolean existing) {
        return new PartnerProfile(SettlementPayee.CREATOR, 7L, "c7", existing ? "c7" : null, "지민의 쇼룸",
                new Contact("김지민", "01098765432", "jimin@example.com"), new Account("004", "KB국민은행", "9876543210", "김지민"),
                null, birthdate, existing);
    }

    private static PartnerProfile withAccount(PartnerProfile p, Account account) {
        return new PartnerProfile(p.payee(), p.refId(), p.partnerRef(), p.externalId(), p.name(), p.contact(), account,
                p.business(), p.birthdate(), p.existing());
    }
}
