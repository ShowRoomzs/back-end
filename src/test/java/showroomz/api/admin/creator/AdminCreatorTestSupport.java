package showroomz.api.admin.creator;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.common.settlement.SettlementTestSupport;
import showroomz.domain.connection.type.ConnectionType;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorWithdrawalRequest;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.repository.CreatorPrivacyAccessLogRepository;
import showroomz.domain.member.creator.repository.CreatorSanctionRepository;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.service.CreatorSanctionService;
import showroomz.domain.member.creator.service.CreatorWithdrawalEligibility;
import showroomz.domain.member.creator.service.CreatorWithdrawalService;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.message.type.MessageCardType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 어드민 인플루언서 상세(§53 · 53 설계서) 통합 테스트 공용 — 본인확인 · 계좌 시드 · 두 번째 운영자 · 이력 · 카드 · 탈퇴 승인 단축 경로.
 * 케이스 번호는 {@code dev/어드민 포스트, 사용자 관리/어드민 인플루언서 상세/53_어드민_인플루언서상세_테스트_시나리오.md}를 따른다.
 *
 * <p>인플루언서 {@code 글로우_지민}(비사업자) · 브랜드 {@code 글로우랩}(PAIR 연결 · 진행 중 공구 1) · 운영자 {@code 김운영} · {@code 박운영}.
 */
public abstract class AdminCreatorTestSupport extends SettlementTestSupport {

    protected static final String CREATORS = "/v1/admin/creators/";
    protected static final String REAL_NAME = "김지민";
    protected static final String PHONE = "01033918842";
    protected static final String ACCOUNT = "333301128842";

    @Autowired protected CreatorAdminHistoryRepository adminHistories;
    @Autowired protected CreatorPrivacyAccessLogRepository privacyLogs;
    @Autowired protected CreatorWithdrawalRequestRepository withdrawalRequests;
    @Autowired protected CreatorSanctionRepository sanctions;
    @Autowired protected CreatorWithdrawalService withdrawalService;
    @Autowired protected CreatorSanctionService sanctionService;
    @Autowired protected CreatorWithdrawalEligibility eligibility;

    protected Seller otherOperator;
    protected String otherAdminToken;
    protected String base;

    @BeforeEach
    void setUpCreatorMemberFixtures() {
        otherOperator = fixture.createAdmin("ops2@showroomz.test", "박운영");
        otherAdminToken = adminToken(otherOperator);
        base = CREATORS + creator.getId();
        jdbc.update("UPDATE creator SET real_name = ?, birthday = ?, phone_number = ?, bank_name = ?, account_number = ? "
                + "WHERE creator_id = ?", REAL_NAME, "1994-06-02", PHONE, "카카오뱅크", ACCOUNT, creator.getId());
    }

    // ------------------------------------------------------------------ 요청

    protected ResultActions adminPatch(String url, Object body) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions adminDelete(String url) throws Exception {
        return mockMvc.perform(delete(url).header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions getAs(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions postAs(String token, String url, Object body) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions creatorPost(String url, Object body) throws Exception {
        return postAs(creatorToken, url, body);
    }

    protected String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ 조회

    /** 저장 이력 — 최신순. */
    protected List<CreatorAdminEventType> events() {
        return adminHistories.findByCreatorIdOrderByCreatedAtDescIdDesc(creator.getId()).stream()
                .map(h -> h.getEventType()).toList();
    }

    /** 운영팀 채널 카드 — 등록 순. */
    protected List<MessageCardType> cardTypes() {
        return jdbc.queryForList("SELECT m.card_type FROM message m JOIN message_thread t ON t.thread_id = m.thread_id "
                        + "JOIN connection c ON c.connection_id = t.connection_id WHERE c.creator_id = ? "
                        + "AND c.type = 'OPERATOR_CREATOR' AND m.card_type IS NOT NULL ORDER BY m.message_id",
                String.class, creator.getId()).stream().map(MessageCardType::valueOf).toList();
    }

    protected long operatorChannelCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM connection WHERE creator_id = ? AND type = ?", Long.class,
                creator.getId(), ConnectionType.OPERATOR_CREATOR.name());
    }

    protected UserStatus userStatus() {
        return UserStatus.valueOf(jdbc.queryForObject("SELECT status FROM users WHERE user_id = ?", String.class,
                creator.getUser().getId()));
    }

    protected Creator reloadCreator() {
        return creatorRepository.findById(creator.getId()).orElseThrow();
    }

    // ------------------------------------------------------------------ 시드

    /** 조건 판정을 거치지 않은 대기 신청 — 조건 미충족 상태의 신청(신청 뒤 상황이 바뀐 건)을 재현한다. */
    protected CreatorWithdrawalRequest savePendingRequest(String reason, LocalDateTime requestedAt) {
        return inTransaction(() -> withdrawalRequests.save(CreatorWithdrawalRequest.request(creator.getId(), reason,
                requestedAt, LocalDate.now().plusDays(5))));
    }

    /** 진행 중 공구를 끝내고 스튜디오 신청 → 어드민 승인까지 — 신청 id. */
    protected Long approveWithdrawal() throws Exception {
        moveTo(groupBuy.getId(), GroupBuyStatus.ENDED);
        CreatorWithdrawalRequest request = withdrawalService.request(creator.getId(), "본업 집중", LocalDateTime.now());
        adminPost(base + "/withdrawal-requests/" + request.getId() + "/approve", Map.of()).andExpect(status().isOk());
        return request.getId();
    }

    /** 이 인플루언서와의 빈 계약 — 상태만 SQL 로 옮긴다(고정 지급비 없음). */
    protected Long contractIn(ContractStatus status) {
        Long contractId = inTransaction(() -> contractRepository.saveAndFlush(
                Contract.createDraft(brand.market(), creator, null)).getId());
        jdbc.update("UPDATE contract SET status = ? WHERE contract_id = ?", status.name(), contractId);
        return contractId;
    }
}
