package showroomz.api.creator.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.repository.CategoryRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractClause;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.repository.ContractClauseVersionRepository;
import showroomz.domain.contract.repository.ContractHistoryRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractClauseVersionStatus;
import showroomz.domain.contract.type.ContractDeclineReason;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.support.BrandFixture;
import showroomz.support.IntegrationTestSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §27 쇼룸 스튜디오 계약 관리 통합 테스트.
 *
 * <p>여기서 지키려는 것은 설계서가 「서버가 집행한다」고 못박은 지점들이다 —
 * 가시성 판정(1-1 · 1-2) · 배지 정의(3-1) · 기한 열 판정(2-2) · 응답에서 덜어내는 것(6) ·
 * 거절의 조건부 UPDATE(5-1) · 재발송 중복 억제(5-2) · 열람 기록의 부수 효과(5-3).
 */
@DisplayName("[통합] 쇼룸 스튜디오 계약 관리")
class CreatorContractIntegrationTest extends IntegrationTestSupport {

    private static final String CONTRACTS = "/v1/creator/contracts";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorRepository creatorRepository;
    @Autowired
    private ConnectionRepository connectionRepository;
    @Autowired
    private MessageThreadRepository messageThreadRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private ContractHistoryRepository contractHistoryRepository;
    @Autowired
    private ContractResendRequestRepository resendRequestRepository;
    @Autowired
    private ContractClauseVersionRepository clauseVersionRepository;

    private BrandFixture.Brand brand;
    private Creator me;
    private String myToken;
    private Connection myConnection;
    private ContractClauseVersion clauseVersion;
    private Product serum;
    private int contractNumberSeq = 1;

    @BeforeEach
    void setUpActors() {
        brand = fixture.createBrand("brand@showroomz.test", "퓨어랩");
        serum = createProduct("수분진정 세럼 30ml", 32_000);
        clauseVersion = seedClauseVersion();

        Users owner = userRepository.save(new Users(
                "creator-소연", "뷰티_소연", "soyeon@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, LocalDateTime.now(), LocalDateTime.now()));
        me = creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/soyeon")
                .accountId("soyeon")
                .followerCount(52_000)
                .businessEmail("biz@showroomz.test")
                .showroomName("뷰티_소연")
                .businessType(CreatorBusinessType.INDIVIDUAL)
                .build());
        myToken = bearerToken(owner.getUsername(), RoleType.CREATOR, owner.getId());

        myConnection = Connection.requestPair(brand.market(), me);
        myConnection.markConnected();
        connectionRepository.save(myConnection);
        messageThreadRepository.save(MessageThread.openFor(myConnection));
    }

    // ── 가시성 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("브랜드가 아직 보내지 않은 계약은 내 creator_id가 박혀 있어도 보이지 않는다 — 가시성은 필터가 아니라 권한이다")
    void hidesContractsNotYetSent() throws Exception {
        Long draft = saveContract(ContractStatus.DRAFT, contract -> { });
        Long reviewPending = saveContract(ContractStatus.REVIEW_PENDING, this::requestReview);
        Long reviewRejected = reviewRejectedContract();
        Long signing = signingContract();

        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].contractId").value(signing));

        // 「있는데 못 본다」(403)를 주면 브랜드가 내 앞으로 계약을 작성 중이라는 사실이 드러난다.
        for (Long hidden : List.of(draft, reviewPending, reviewRejected)) {
            mockMvc.perform(get(CONTRACTS + "/" + hidden).header(HttpHeaders.AUTHORIZATION, myToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CONTRACT_NOT_RECEIVED"));
        }
    }

    @Test
    @DisplayName("남의 계약과 미도착 계약이 같은 문구로 404다 — 화면에서 구분할 수 없어야 한다")
    void hidesOthersContractsWithTheSameMessage() throws Exception {
        Creator other = createOtherCreator();
        Long notMine = saveContractFor(other, ContractStatus.SIGNING, this::approve);
        Long notSentToMe = reviewRejectedContract();

        mockMvc.perform(get(CONTRACTS + "/" + notMine).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 계약입니다."));
        mockMvc.perform(get(CONTRACTS + "/" + notSentToMe).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 계약입니다."));
    }

    @Test
    @DisplayName("종결된 계약은 200으로 읽힌다 — 거절·만료·취소 상세(S7·S8·S9)")
    void closedContractsRemainReadable() throws Exception {
        Long declined = declinedContract();

        mockMvc.perform(get(CONTRACTS + "/" + declined).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.closure.actorType").value("CREATOR"))
                .andExpect(jsonPath("$.closure.reasonCode").value("SCHEDULE_MISMATCH"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("일정이 맞지 않음"));
    }

    // ── 배지 · 탭 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("배지는 내 서명이 남은 건수다 — 브랜드 서명 여부를 보지 않는다(순서가 아니라 각자 한다)")
    void badgeCountsMyPendingSignatureRegardlessOfBrand() throws Exception {
        signingContract();                                    // S3  — 양측 미서명 · 센다
        saveContract(ContractStatus.SIGNING, contract -> {    // S3a — 브랜드만 서명 · 센다
            approve(contract);
            contract.updateSignatures(LocalDateTime.now(), null, LocalDateTime.now());
        });
        saveContract(ContractStatus.SIGNING, contract -> {    // S3b — 내 서명 완료 · 세지 않는다
            approve(contract);
            contract.updateSignatures(null, LocalDateTime.now(), LocalDateTime.now());
        });
        concludedContract();                                  // 조치 아님

        mockMvc.perform(get(CONTRACTS + "/summary").header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabCounts.ALL").value(4))
                .andExpect(jsonPath("$.tabCounts.SIGNING").value(3))
                .andExpect(jsonPath("$.tabCounts.CONCLUDED").value(1))
                .andExpect(jsonPath("$.tabCounts.CLOSED").value(0))
                // 작성중 탭은 0건이 아니라 키 자체가 없다.
                .andExpect(jsonPath("$.tabCounts.DRAFT").doesNotExist())
                .andExpect(jsonPath("$.actionRequiredCount").value(2));
    }

    @Test
    @DisplayName("체결 처리 대기는 서명 진행중과 다른 탭이다 — 파트너처럼 묶지 않는다")
    void separatesConclusionPendingTab() throws Exception {
        signingContract();
        saveContract(ContractStatus.CONCLUSION_PENDING, contract -> {
            approve(contract);
            contract.updateSignatures(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        });

        mockMvc.perform(get(CONTRACTS + "/summary").header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.tabCounts.SIGNING").value(1))
                .andExpect(jsonPath("$.tabCounts.CONCLUSION_PENDING").value(1))
                // 운영자 대기라 내가 할 수 있는 일이 없다.
                .andExpect(jsonPath("$.actionRequiredCount").value(1));
    }

    // ── 「내 서명 기한」 열 ──────────────────────────────────────────────────

    @Test
    @DisplayName("기한 열은 상태 × 서명 조합으로 갈린다 — 서버가 판정해 내린다")
    void serverDecidesDeadlineColumn() throws Exception {
        saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().plusDays(30)));

        mockMvc.perform(get(CONTRACTS + "?tab=SIGNING").header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.content[0].deadline.type").value("DEADLINE"))
                .andExpect(jsonPath("$.content[0].deadline.deadlineAt").exists())
                // 기한이 멀면 중립색이다.
                .andExpect(jsonPath("$.content[0].deadline.tone").value("NEUTRAL"));

        assertDeadlineType(concludedContract(), "SIGNED");
        assertDeadlineType(expiredContract(), "PASSED");
        assertDeadlineType(declinedContract(), "NONE");
    }

    @Test
    @DisplayName("서명 기한이 D-3 안이면 경고색이다 — DANGER는 이 열에 존재하지 않는다")
    void marksImminentDeadlineAsWarning() throws Exception {
        saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().plusDays(2)));

        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.content[0].deadline.type").value("DEADLINE"))
                .andExpect(jsonPath("$.content[0].deadline.tone").value("WARNING"));
    }

    @Test
    @DisplayName("내가 서명을 마치면 기한 대신 「내 서명 완료」다")
    void showsMySignedInsteadOfDeadline() throws Exception {
        Long contractId = saveContract(ContractStatus.SIGNING, contract -> {
            approve(contract, LocalDateTime.now().plusDays(1));
            contract.updateSignatures(null, LocalDateTime.now(), LocalDateTime.now());
        });

        assertDeadlineType(contractId, "MY_SIGNED");
    }

    // ── 상세 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("상세는 브랜드 내부 정보를 싣지 않는다 — 화면에 안 그려도 JSON에 실리면 이미 유출이다")
    void omitsBrandInternals() throws Exception {
        Long contractId = signingContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.review").doesNotExist())
                .andExpect(jsonPath("$.warningFlags").doesNotExist())
                .andExpect(jsonPath("$.sourceContractId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                // 스텝퍼의 「운영자 검토 통과」에 필요한 한 값만 살아남는다.
                .andExpect(jsonPath("$.stepper.reviewApprovedAt").exists());
    }

    @Test
    @DisplayName("상품 항목은 스튜디오 관점의 이름으로 내려가고 예상 리워드는 공유 유틸이 계산한다")
    void usesStudioFacingItemFields() throws Exception {
        Long contractId = signingContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.items[0].myRewardRate").value(15.0))
                .andExpect(jsonPath("$.items[0].brandSupplyQuantity").value(300))
                // 28,000 × 15% = 4,200 · 1원 단위 버림
                .andExpect(jsonPath("$.items[0].expectedUnitReward").value(4200))
                .andExpect(jsonPath("$.items[0].rewardRate").doesNotExist())
                .andExpect(jsonPath("$.items[0].minQuantity").doesNotExist());
    }

    @Test
    @DisplayName("서명 진행중에는 「내가 받는 금액」이 내려가고 미보증 고지와 스레드 id가 함께 온다")
    void sendsPayoutFromSigning() throws Exception {
        Long contractId = signingContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.payout.fixedFeeAmount").value(1_200_000))
                .andExpect(jsonPath("$.payout.fixedFeeTrigger").value("POST_REGISTERED"))
                .andExpect(jsonPath("$.payout.rewardRates[0].rate").value(15.0))
                .andExpect(jsonPath("$.payout.settlementTiming").value("GROUP_BUY_ENDED"))
                .andExpect(jsonPath("$.payout.platformGuaranteed").value(false))
                // 문구만 두면 인플루언서가 어디로 가야 하는지 모른다.
                .andExpect(jsonPath("$.payout.disputeChannel.threadId").exists())
                .andExpect(jsonPath("$.brand.threadId").exists())
                .andExpect(jsonPath("$.settlement.withholdingType").value("WITHHOLDING_3_3"));
    }

    @Test
    @DisplayName("종결 3종에서 payout은 null이고 콘텐츠 의무는 남되 효력 없음으로 표기된다")
    void dropsPayoutButKeepsObligationOnClosure() throws Exception {
        Long contractId = declinedContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.content.feedCount").value(1))
                .andExpect(jsonPath("$.content.obligationAlive").value(false))
                // 계약이 성립하지 않아 지급 없음이다.
                .andExpect(jsonPath("$.fixedFee.paymentState").value("NONE"));
    }

    @Test
    @DisplayName("체결완료는 「지급 전」으로 내려간다 — 체결을 입금으로 오해하면 분쟁이 된다")
    void marksConcludedAsNotYetPaid() throws Exception {
        Long contractId = concludedContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.fixedFee.paymentState").value("NOT_YET"))
                // 원시값은 내리지 않는다.
                .andExpect(jsonPath("$.fixedFee.paidAt").doesNotExist());

        transactionTemplate.executeWithoutResult(status ->
                contractRepository.markFixedFeePaid(contractId, LocalDateTime.now()));

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                // PAID가 아니다 — 브랜드가 버튼을 누른 사실일 뿐 입금 사실이 아니다.
                .andExpect(jsonPath("$.fixedFee.paymentState").value("RECORDED_BY_BRAND"));
    }

    @Test
    @DisplayName("서명 현황을 아직 갱신하지 않은 계약도 기준 시각을 내린다 — 발송 시각으로 대체한다")
    void alwaysSendsSignatureAsOf() throws Exception {
        Long contractId = signingContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.signature.asOf").exists())
                .andExpect(jsonPath("$.signature.brandSignedAt").doesNotExist())
                .andExpect(jsonPath("$.signature.creatorSignedAt").doesNotExist());
    }

    @Test
    @DisplayName("이력은 화이트리스트 7종만 내려간다 — 검토 반려·브랜드 내부 행위는 빠진다")
    void filtersHistoryInTheQuery() throws Exception {
        Long contractId = signingContract();
        Contract contract = contractRepository.findById(contractId).orElseThrow();

        LocalDateTime base = LocalDateTime.now().minusDays(2);
        saveHistory(contract, ContractEventType.CREATED, ContractActorType.SELLER, base);
        saveHistory(contract, ContractEventType.REVIEW_REQUESTED, ContractActorType.SELLER, base.plusMinutes(10));
        saveHistory(contract, ContractEventType.REVIEW_REJECTED, ContractActorType.ADMIN, base.plusMinutes(20));
        saveHistory(contract, ContractEventType.REVIEW_APPROVED, ContractActorType.ADMIN, base.plusMinutes(30));
        saveHistory(contract, ContractEventType.SIGNATURE_SENT, ContractActorType.ADMIN, base.plusMinutes(40));
        saveHistory(contract, ContractEventType.RESEND_REQUESTED, ContractActorType.SELLER, base.plusMinutes(50));

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.history[*].eventType", hasItem("SIGNATURE_SENT")))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("CREATED"))))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("REVIEW_REQUESTED"))))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("REVIEW_REJECTED"))))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("REVIEW_APPROVED"))))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("RESEND_REQUESTED"))))
                // 연결 성립은 계약 이력이 아니라 Connection에서 합성해 맨 아래 한 줄로 붙는다.
                .andExpect(jsonPath("$.history[1].eventType").doesNotExist())
                .andExpect(jsonPath("$.history[1].actorType").value("SELLER"));
    }

    @Test
    @DisplayName("권한 5종 — 내 서명이 남았으면 거절·재발송이 열리고 종결 후에도 스레드는 열린다")
    void decidesPermissions() throws Exception {
        Long signing = signingContract();
        mockMvc.perform(get(CONTRACTS + "/" + signing).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.permissions.canDecline").value(true))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(true))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true))
                .andExpect(jsonPath("$.permissions.canDownloadDocuments").value(false))
                .andExpect(jsonPath("$.permissions.canOpenGroupBuy").value(false));

        // 서명 후에는 액션이 [스레드에서 협의하기] 하나로 줄어든다(S3b).
        Long mySigned = saveContract(ContractStatus.SIGNING, contract -> {
            approve(contract);
            contract.updateSignatures(null, LocalDateTime.now(), LocalDateTime.now());
        });
        mockMvc.perform(get(CONTRACTS + "/" + mySigned).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.permissions.canDecline").value(false))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(false))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true));

        // 회복 경로는 종결 후에도 남는다(§27-1 #5).
        mockMvc.perform(get(CONTRACTS + "/" + declinedContract()).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true));
    }

    @Test
    @DisplayName("이전/다음은 목록 조건을 함께 받았을 때만 계산된다")
    void resolvesNeighborsOnlyWithListContext() throws Exception {
        Long older = saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(5)));
        Long newer = saveContract(ContractStatus.SIGNING, contract ->
                approve(contract, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(9)));

        // 목록 조건이 없으면 버튼을 비활성할 근거만 내린다.
        mockMvc.perform(get(CONTRACTS + "/" + newer).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.navigation.prevContractId").doesNotExist())
                .andExpect(jsonPath("$.navigation.nextContractId").doesNotExist());

        // 받은 순(기본)에서 newer가 위, older가 아래다.
        mockMvc.perform(get(CONTRACTS + "/" + newer + "?tab=ALL&sort=RECEIVED_DESC")
                        .header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.navigation.prevContractId").doesNotExist())
                .andExpect(jsonPath("$.navigation.nextContractId").value(older));

        mockMvc.perform(get(CONTRACTS + "/" + older + "?tab=ALL&sort=RECEIVED_DESC")
                        .header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.navigation.prevContractId").value(newer))
                .andExpect(jsonPath("$.navigation.nextContractId").doesNotExist());
    }

    @Test
    @DisplayName("상세 최초 진입이 열람 시각을 찍는다 — 별도 API를 두지 않는다")
    void stampsFirstViewAsASideEffect() throws Exception {
        Long contractId = signingContract();
        assertThat(contractRepository.findById(contractId).orElseThrow().getCreatorViewedAt()).isNull();

        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk());

        LocalDateTime firstView = contractRepository.findById(contractId).orElseThrow().getCreatorViewedAt();
        assertThat(firstView).isNotNull();

        // 최초 1회 CAS라 멱등이다 — 두 번째 조회가 시각을 덮어쓰지 않는다.
        mockMvc.perform(get(CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk());
        assertThat(contractRepository.findById(contractId).orElseThrow().getCreatorViewedAt())
                .isEqualTo(firstView);
    }

    @Test
    @DisplayName("조항은 계약이 고정한 버전을 읽는다 — 내가 서명한 조항과 화면이 달라지면 안 된다")
    void readsTheClauseVersionPinnedToTheContract() throws Exception {
        Long contractId = signingContract();

        mockMvc.perform(get(CONTRACTS + "/" + contractId + "/clauses")
                        .header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clauseVersionId").value(clauseVersion.getId()))
                .andExpect(jsonPath("$.versionNumber").value("1.0"))
                .andExpect(jsonPath("$.clauses[0].code").value("PRICE_POLICY"));
    }

    // ── 쓰기 2종 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("거절은 이 계약만 종결시킨다 — 연결은 끊지 않는다")
    void declineClosesOnlyTheContract() throws Exception {
        Long contractId = signingContract();

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/decline")
                        .header(HttpHeaders.AUTHORIZATION, myToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCode":"CONDITION_RENEGOTIATION","memo":"리워드율을 조금 더 논의하고 싶습니다."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.closure.actorType").value("CREATOR"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("조건 재협의 필요"))
                .andExpect(jsonPath("$.closure.memo").value("리워드율을 조금 더 논의하고 싶습니다."))
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.permissions.canDecline").value(false));

        assertThat(connectionRepository.findById(myConnection.getId()).orElseThrow().getStatus())
                .isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(contractHistoryRepository.findByContractIdOrderByOccurredAtAscIdAsc(contractId))
                .anyMatch(entry -> entry.getEventType() == ContractEventType.DECLINED
                        && entry.getActorType() == ContractActorType.CREATOR);
    }

    @Test
    @DisplayName("ETC는 메모 없이도 통과한다 — 시안에 `*`가 없다(미결 #2)")
    void allowsEtcWithoutMemo() throws Exception {
        Long contractId = signingContract();

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/decline")
                        .header(HttpHeaders.AUTHORIZATION, myToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCode":"ETC"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closure.reasonCode").value("ETC"))
                .andExpect(jsonPath("$.closure.memo").doesNotExist());
    }

    @Test
    @DisplayName("내 서명이 이미 들어간 계약은 거절되지 않는다 — 모달을 열어 둔 사이 운영자가 체크할 수 있다")
    void refusesDeclineAfterMySignature() throws Exception {
        Long contractId = saveContract(ContractStatus.SIGNING, contract -> {
            approve(contract);
            contract.updateSignatures(null, LocalDateTime.now(), LocalDateTime.now());
        });

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/decline")
                        .header(HttpHeaders.AUTHORIZATION, myToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCode":"ETC"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_ALREADY_SIGNED"));

        assertThat(contractRepository.findById(contractId).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.SIGNING);
    }

    @Test
    @DisplayName("체결 처리 대기는 거절 대상이 아니다 — 양측 서명이 끝났다")
    void refusesDeclineOnConclusionPending() throws Exception {
        Long contractId = saveContract(ContractStatus.CONCLUSION_PENDING, contract -> {
            approve(contract);
            contract.updateSignatures(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        });

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/decline")
                        .header(HttpHeaders.AUTHORIZATION, myToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCode":"ETC"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_ALREADY_SIGNED"));
    }

    @Test
    @DisplayName("재발송은 요청일 뿐이고 연타해도 행이 하나다")
    void suppressesDuplicateResendRequests() throws Exception {
        Long contractId = signingContract();

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/resend-request")
                        .header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRequested").value(false));

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/resend-request")
                        .header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRequested").value(true));

        List<ContractResendRequest> requests =
                resendRequestRepository.findByContractIdOrderByRequestedAtDescIdDesc(contractId);
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getRequesterType()).isEqualTo(ContractActorType.CREATOR);

        // 상태는 변하지 않는다 — 요청은 발송이 아니다.
        assertThat(contractRepository.findById(contractId).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.SIGNING);
    }

    @Test
    @DisplayName("이미 서명한 사람에게 재발송할 이유가 없다 — 거절과 허용 조건이 같다")
    void refusesResendAfterMySignature() throws Exception {
        Long contractId = saveContract(ContractStatus.SIGNING, contract -> {
            approve(contract);
            contract.updateSignatures(null, LocalDateTime.now(), LocalDateTime.now());
        });

        mockMvc.perform(post(CONTRACTS + "/" + contractId + "/resend-request")
                        .header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_RESEND_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("미도착 계약에는 쓰기도 404다 — 존재 여부 자체를 숨긴다")
    void hidesNotYetSentContractsFromWrites() throws Exception {
        Long reviewRejected = reviewRejectedContract();

        mockMvc.perform(post(CONTRACTS + "/" + reviewRejected + "/decline")
                        .header(HttpHeaders.AUTHORIZATION, myToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reasonCode":"ETC"}"""))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(CONTRACTS + "/" + reviewRejected + "/resend-request")
                        .header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isNotFound());
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private void assertDeadlineType(Long contractId, String expectedType) throws Exception {
        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(jsonPath("$.content[?(@.contractId == %d)].deadline.type".formatted(contractId))
                        .value(hasItem(expectedType)));
    }

    private Long signingContract() {
        return saveContract(ContractStatus.SIGNING, this::approve);
    }

    private Long concludedContract() {
        return saveContract(ContractStatus.CONCLUDED, contract -> {
            approve(contract);
            contract.updateSignatures(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
            contract.conclude(LocalDateTime.now());
        });
    }

    private Long expiredContract() {
        return saveContract(ContractStatus.EXPIRED, contract -> {
            approve(contract, LocalDateTime.now().minusDays(1));
            contract.expire(LocalDateTime.now());
        });
    }

    /** 거절은 실제 조건부 UPDATE 경로를 태운다 — applyCanceled는 주체가 SELLER라 여기 쓸 수 없다. */
    private Long declinedContract() {
        Long contractId = signingContract();
        transactionTemplate.executeWithoutResult(tx -> contractRepository.declineByCreator(
                contractId, me.getId(), ContractDeclineReason.SCHEDULE_MISMATCH.name(),
                null, LocalDateTime.now()));
        return contractId;
    }

    private Long reviewRejectedContract() {
        return saveContract(ContractStatus.REVIEW_REJECTED, contract -> {
            requestReview(contract);
            contract.rejectReview("PRICE_POLICY", "공구가가 정가보다 높습니다.", LocalDateTime.now());
        });
    }

    private void requestReview(Contract contract) {
        contract.applyReviewRequested(nextContractNumber(), clauseVersion, null, LocalDateTime.now());
    }

    private void approve(Contract contract) {
        approve(contract, LocalDateTime.now().plusDays(8));
    }

    private void approve(Contract contract, LocalDateTime deadlineAt) {
        approve(contract, LocalDateTime.now().minusDays(1), deadlineAt);
    }

    private void approve(Contract contract, LocalDateTime requestedAt, LocalDateTime deadlineAt) {
        contract.applyReviewRequested(nextContractNumber(), clauseVersion, null, requestedAt.minusHours(1));
        contract.approveReview(requestedAt, deadlineAt, requestedAt);
    }

    private String nextContractNumber() {
        return "CTR-20260813-" + String.format("%05d", contractNumberSeq++);
    }

    private Long saveContract(ContractStatus status, java.util.function.Consumer<Contract> shape) {
        return saveContractFor(me, status, shape);
    }

    /**
     * 어드민 API 없이 상태를 직접 만든다 — Q4는 어드민 P7과 순서 의존이 없고(설계서 9),
     * S3b 같은 분기는 값을 직접 넣어야 탄다.
     */
    private Long saveContractFor(Creator creator, ContractStatus status,
                                 java.util.function.Consumer<Contract> shape) {
        return transactionTemplate.execute(tx -> {
            Connection connection = creator.getId().equals(me.getId()) ? myConnection : null;
            Contract contract = Contract.createDraft(brand.market(), creator, connection);
            contract.updateTerms(
                    "여름 수분 세럼 공구",
                    LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(26),
                    1_200_000, FixedFeeTrigger.POST_REGISTERED, LocalDateTime.now(),
                    1, 1, 3, LocalDate.now().plusDays(30),
                    true, SecondaryUsePeriodType.FIXED, 12, false, "비고");

            ContractItem item = ContractItem.builder()
                    .product(serum)
                    .productName(serum.getName())
                    .regularPrice(serum.getRegularPrice())
                    .groupBuyPrice(28_000)
                    .rewardRate(new BigDecimal("15.0"))
                    .minQuantity(300)
                    .sortOrder(0)
                    .build();
            contract.replaceItems(new ArrayList<>(List.of(item)));

            shape.accept(contract);
            // 종결·만료는 전이 메서드가 상태를 정하므로 요청한 상태와 어긋나지 않는지만 확인한다.
            Contract saved = contractRepository.saveAndFlush(contract);
            assertThat(saved.getStatus()).isEqualTo(status);
            return saved.getId();
        });
    }

    private void saveHistory(Contract contract, ContractEventType eventType,
                             ContractActorType actorType, LocalDateTime occurredAt) {
        contractHistoryRepository.save(ContractHistory.of(
                contract, eventType, actorType, null, "표시명", null, occurredAt));
    }

    private Creator createOtherCreator() {
        Users owner = userRepository.save(new Users(
                "creator-지민", "글로우_지민", "jimin@showroomz.test", "Y", null,
                ProviderType.LOCAL, RoleType.CREATOR, LocalDateTime.now(), LocalDateTime.now()));
        return creatorRepository.save(Creator.builder()
                .user(owner)
                .snsType(SnsType.INSTAGRAM)
                .channelUrl("https://instagram.com/jimin")
                .accountId("jimin")
                .followerCount(12_000)
                .businessEmail("biz2@showroomz.test")
                .showroomName("글로우_지민")
                .businessType(CreatorBusinessType.BUSINESS)
                .build());
    }

    private Product createProduct(String name, int regularPrice) {
        Category category = new Category();
        category.setName("뷰티 " + name);
        categoryRepository.save(category);

        Product product = new Product();
        product.setMarket(brand.market());
        product.setCategory(category);
        product.setName(name);
        product.setRegularPrice(regularPrice);
        product.setSalePrice(regularPrice);
        product.setDisplayStatus(ProductDisplayStatus.DISPLAY);
        return productRepository.save(product);
    }

    /** Flyway가 꺼진 통합 테스트 프로필에서는 V120의 조항 seed가 없다 — 최소 버전을 직접 적재한다. */
    private ContractClauseVersion seedClauseVersion() {
        ContractClauseVersion version = ContractClauseVersion.builder()
                .versionNumber("1.0")
                .effectiveDate(LocalDate.now().minusDays(1))
                .status(ContractClauseVersionStatus.EFFECTIVE)
                .clauses(new ArrayList<>())
                .build();

        version.getClauses().add(ContractClause.builder()
                .clauseVersion(version)
                .code("PRICE_POLICY")
                .sortOrder(1)
                .summaryTitle("가격 정책")
                .summaryDescription("공구 기간 중 타 채널 최저가 준수")
                .fullTitle("제3조 최저가 정책")
                .fullBody("브랜드는 공구 기간 중 동일 상품을 공구가보다 낮은 가격으로 타 채널에 판매하지 않는다.")
                .build());

        return clauseVersionRepository.save(version);
    }
}
