package showroomz.api.creator.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import showroomz.api.admin.contract.dto.AdminContractDto.DownloadResponse;
import showroomz.api.admin.contract.service.ContractDocumentStorage;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractDeclineReason;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.market.entity.Market;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기획서 {@code ui-studio-08-contracts.html}(rev.2) 화면 대조 — <b>화면이 서버에서 받아야 그려지는 값</b>.
 *
 * <p>시안 12종 중 서버가 관여하는 10종(S1 · S2 · S3 · S3a · S3b · S3c · S5 · S6 · S7 · S8 · S9)을
 * 화면 단위로 하나씩 대조한다. S11(내 서명 완료 모달)은 서버가 띄우지 않지만(설계서 0-3)
 * 「내 서명 ≠ 체결」이라는 그 모달의 전제는 S3b에서 서버 값으로 확인한다.
 *
 * <p>여기 적힌 수치(8건 · 2/1/2/3 · 배지 1 · 4,200원 · 2,640원 · 8일)는 시안이 그린 값 그대로다.
 */
@DisplayName("[통합] 쇼룸 스튜디오 계약 화면 대조")
class CreatorContractScreenSpecIntegrationTest extends CreatorContractTestSupport {

    @MockitoBean
    private ContractDocumentStorage contractDocumentStorage;

    @BeforeEach
    void stubStorage() {
        when(contractDocumentStorage.download(any())).thenAnswer(invocation -> {
            ContractDocument document = invocation.getArgument(0);
            return new DownloadResponse(
                    "https://signed.test/" + document.getDocumentType(),
                    document.getOriginalName(), document.getSizeBytes(), 300, null);
        });
    }

    // ── S1 · 목록 기본 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("S1 — 시안의 8행이 받은 순으로 서고 각 행의 브랜드·상품 수·상태·「내 서명 기한」 열이 시안과 같다")
    void s1ListMatchesScreen() throws Exception {
        LocalDateTime imminentDeadline = LocalDateTime.now().plusDays(2).withNano(0);
        seedS1Rows(imminentDeadline);

        list()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(8))
                .andExpect(jsonPath("$.content[*].title").value(contains(
                        "여름 수분 세럼 공구", "가을 앰플 신제품 공구", "퓨어랩 립밤 재입고 공구", "글로우 크림 앵콜 공구",
                        "여름 미스트 기획 공구", "봄 클렌저 공구", "수분 토너 리뉴얼 공구", "클렌징 오일 여름 공구")))
                // 「브랜드」 열 — 상대 라벨은 브랜드다([용어]).
                .andExpect(jsonPath("$.content[*].brandName").value(contains(
                        "○○ 브랜드", "△△ 코스메틱", "퓨어랩", "△△ 코스메틱",
                        "□□ 뷰티랩", "□□ 뷰티랩", "○○ 브랜드", "△△ 코스메틱")))
                .andExpect(jsonPath("$.content[*].itemCount").value(contains(2, 1, 2, 1, 1, 1, 1, 1)))
                .andExpect(jsonPath("$.content[*].statusLabel").value(contains(
                        "서명 진행중", "서명 진행중", "체결 처리 대기", "체결완료",
                        "체결완료", "거절", "만료", "취소")))
                // 상태 색은 파트너센터와 같다(원칙②③) — 서명 진행중을 「내 몫」이라고 경고색으로 바꾸지 않는다.
                .andExpect(jsonPath("$.content[*].statusTone").value(contains(
                        "INFO", "INFO", "INFO", "SUCCESS", "SUCCESS", "DANGER", "NEUTRAL", "NEUTRAL")))
                // 「내 서명 기한」 열 — 날짜 · 내 서명 완료 · 양측 서명 완료 · 서명 완료 · — · 기한 경과.
                .andExpect(jsonPath("$.content[*].deadline.type").value(contains(
                        "DEADLINE", "MY_SIGNED", "BOTH_SIGNED", "SIGNED", "SIGNED", "NONE", "PASSED", "NONE")))
                // 경고색은 조치할 첫 행 하나뿐이고, 위험색은 이 열에 없다.
                .andExpect(jsonPath("$.content[*].deadline.tone").value(contains(
                        "WARNING", "NEUTRAL", "NEUTRAL", "NEUTRAL", "NEUTRAL", "NEUTRAL", "NEUTRAL", "NEUTRAL")))
                .andExpect(jsonPath("$.content[0].deadline.deadlineAt").value(iso(imminentDeadline)))
                // 날짜는 내 서명이 남은 행에만 — 나머지는 판정 결과만 내린다.
                .andExpect(jsonPath("$.content[1].deadline.deadlineAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].startAt").value(iso(screenStartAt())))
                .andExpect(jsonPath("$.content[0].endAt").value(iso(screenEndAt())))
                // 「생성일」 열을 「내 서명 기한」으로 교체했다 — 생성일은 브랜드의 사정이다(§27-1 #3).
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                // 행 클릭 진입 · 관리 열 없음 — 작성 모드로 들어갈 일이 없다.
                .andExpect(jsonPath("$.content[0].entryMode").doesNotExist());
    }

    @Test
    @DisplayName("S1 — 탭은 전체 8 · 서명 진행중 2 · 체결 처리 대기 1 · 체결완료 2 · 종료 3이고, 「내 서명이 필요한 계약」은 1건이다")
    void s1TabCountsAndBadgeMatchScreen() throws Exception {
        seedS1Rows(LocalDateTime.now().plusDays(2));

        summary()
                .andExpect(status().isOk())
                // 작성중 탭이 없다 — 5종뿐이다.
                .andExpect(jsonPath("$.tabCounts", aMapWithSize(5)))
                .andExpect(jsonPath("$.tabCounts.ALL").value(8))
                .andExpect(jsonPath("$.tabCounts.SIGNING").value(2))
                .andExpect(jsonPath("$.tabCounts.CONCLUSION_PENDING").value(1))
                .andExpect(jsonPath("$.tabCounts.CONCLUDED").value(2))
                .andExpect(jsonPath("$.tabCounts.CLOSED").value(3))
                // GNB #3 배지 · 목록 헤더 「총 8건 · 내 서명이 필요한 계약 1건」 — 가을 앰플(내 서명 완료)은 세지 않는다.
                .andExpect(jsonPath("$.actionRequiredCount").value(1));

        // 탭 카운트와 탭 목록이 같은 판정을 쓴다 — 숫자는 3인데 목록이 2건이면 화면이 거짓말을 한다.
        for (String[] tab : List.of(
                new String[]{"SIGNING", "2"}, new String[]{"CONCLUSION_PENDING", "1"},
                new String[]{"CONCLUDED", "2"}, new String[]{"CLOSED", "3"})) {
            list("tab", tab[0]).andExpect(jsonPath("$.pageInfo.totalResults").value(Integer.parseInt(tab[1])));
        }
    }

    // ── S2 · 빈 상태 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("S2 — 브랜드가 작성·검토 중인 계약만 있으면 「받은 계약이 없습니다」다 — 탭 5종 모두 0")
    void s2EmptyStateWhenNothingArrived() throws Exception {
        saveContract(ContractStatus.DRAFT, contract -> { });
        saveContract(ContractStatus.REVIEW_PENDING, this::requestReview);
        reviewRejectedContract();

        list()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.pageInfo.totalResults").value(0));

        summary()
                .andExpect(jsonPath("$.tabCounts", aMapWithSize(5)))
                .andExpect(jsonPath("$.tabCounts.ALL").value(0))
                .andExpect(jsonPath("$.tabCounts.SIGNING").value(0))
                .andExpect(jsonPath("$.tabCounts.CONCLUSION_PENDING").value(0))
                .andExpect(jsonPath("$.tabCounts.CONCLUDED").value(0))
                .andExpect(jsonPath("$.tabCounts.CLOSED").value(0))
                .andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    // ── S3 · 서명 진행중(양측 미서명) ──────────────────────────────────────

    @Test
    @DisplayName("S3 — 헤더·스텝퍼·서명 카드·「내가 받는 금액」·「내가 해야 할 일」·상품 항목이 시안 값 그대로 내려간다")
    void s3DetailCarriesEveryCardOfTheScreen() throws Exception {
        LocalDateTime requestedAt = LocalDateTime.now().minusHours(3).withNano(0);
        LocalDateTime deadlineAt = LocalDateTime.now().plusDays(8).withNano(0);
        Long contractId = saveContract(ContractStatus.SIGNING, contract -> {
            applyScreenTerms(contract, "여름 수분 세럼 공구");
            approve(contract, requestedAt, deadlineAt);
        });
        String contractNumber = load(contractId).getContractNumber();

        detail(contractId)
                .andExpect(status().isOk())
                // 헤더 — 「CTR-… · 브랜드 · … 받음」
                .andExpect(jsonPath("$.contractNumber").value(contractNumber))
                .andExpect(jsonPath("$.title").value("여름 수분 세럼 공구"))
                .andExpect(jsonPath("$.brand.name").value("퓨어랩"))
                .andExpect(jsonPath("$.receivedAt").value(iso(requestedAt)))
                .andExpect(jsonPath("$.status").value("SIGNING"))
                .andExpect(jsonPath("$.statusLabel").value("서명 진행중"))
                .andExpect(jsonPath("$.statusTone").value("INFO"))
                // 4단 스텝퍼 — 운영자 검토 통과 → 서명 요청 발송 → 양측 서명 0/2 → 운영자 체결 완료 처리
                .andExpect(jsonPath("$.stepper.reviewApprovedAt").value(iso(requestedAt)))
                .andExpect(jsonPath("$.stepper.signatureRequestedAt").value(iso(requestedAt)))
                .andExpect(jsonPath("$.stepper.signedCount").value(0))
                .andExpect(jsonPath("$.stepper.concludedAt").doesNotExist())
                // 서명 카드 — 브랜드 서명 대기 · 나 서명 대기 · 기한
                .andExpect(jsonPath("$.signature.deadlineAt").value(iso(deadlineAt)))
                .andExpect(jsonPath("$.signature.brandSignedAt").doesNotExist())
                .andExpect(jsonPath("$.signature.creatorSignedAt").doesNotExist())
                // 운영자가 아직 한 번도 확인하지 않았다 — 발송 시각이 기준 시각이다.
                .andExpect(jsonPath("$.signature.asOf").value(iso(requestedAt)))
                // 계약 조건 — 브랜드 · 스레드 열기 · 공구 기간 (8일)
                .andExpect(jsonPath("$.brand.threadId").value(myThread.getId()))
                .andExpect(jsonPath("$.brand.connected").value(true))
                .andExpect(jsonPath("$.period.startAt").value(iso(screenStartAt())))
                .andExpect(jsonPath("$.period.endAt").value(iso(screenEndAt())))
                .andExpect(jsonPath("$.period.days").value(8))
                // 「내가 받는 금액」 — 고정 지급비 · 지급 시점 · 상품별 리워드율 · 정산 시점 · 미보증 고지
                .andExpect(jsonPath("$.payout.fixedFeeAmount").value(1_200_000))
                .andExpect(jsonPath("$.payout.fixedFeeTrigger").value("POST_REGISTERED"))
                .andExpect(jsonPath("$.payout.fixedFeeTriggerLabel").value("공구 게시물 등록 후"))
                .andExpect(jsonPath("$.payout.rewardRates[*].productName")
                        .value(contains("수분진정 세럼 30ml", "수분진정 크림 50ml")))
                .andExpect(jsonPath("$.payout.rewardRates[*].rate").value(contains(15.0, 12.0)))
                .andExpect(jsonPath("$.payout.settlementTiming").value("GROUP_BUY_ENDED"))
                .andExpect(jsonPath("$.payout.platformGuaranteed").value(false))
                .andExpect(jsonPath("$.payout.disputeChannel.threadId").value(myThread.getId()))
                // 「내가 해야 할 일」 — 피드 1 · 릴스 1 · 스토리 3 · 게시 완료 기한 · 2차 활용 12개월 · 사전 검수 있음
                .andExpect(jsonPath("$.content.feedCount").value(1))
                .andExpect(jsonPath("$.content.reelsCount").value(1))
                .andExpect(jsonPath("$.content.storyCount").value(3))
                .andExpect(jsonPath("$.content.dueDate").value(screenEndAt().toLocalDate().toString()))
                .andExpect(jsonPath("$.content.secondaryUseAllowed").value(true))
                .andExpect(jsonPath("$.content.secondaryUsePeriodType").value("FIXED"))
                .andExpect(jsonPath("$.content.secondaryUseMonths").value(12))
                .andExpect(jsonPath("$.content.preReview").value(true))
                .andExpect(jsonPath("$.content.note").value(org.hamcrest.Matchers.startsWith("2차 활용 범위")))
                .andExpect(jsonPath("$.content.obligationAlive").value(true))
                // 계약 상품 항목 2건 — 정가 · 공구가 · 내 리워드율 · 개당 리워드 · 브랜드 준비 물량
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value("수분진정 세럼 30ml"))
                .andExpect(jsonPath("$.items[0].regularPrice").value(32_000))
                .andExpect(jsonPath("$.items[0].groupBuyPrice").value(28_000))
                .andExpect(jsonPath("$.items[0].myRewardRate").value(15.0))
                .andExpect(jsonPath("$.items[0].expectedUnitReward").value(4_200))
                .andExpect(jsonPath("$.items[0].brandSupplyQuantity").value(300))
                .andExpect(jsonPath("$.items[1].productName").value("수분진정 크림 50ml"))
                .andExpect(jsonPath("$.items[1].regularPrice").value(24_000))
                .andExpect(jsonPath("$.items[1].groupBuyPrice").value(22_000))
                .andExpect(jsonPath("$.items[1].myRewardRate").value(12.0))
                .andExpect(jsonPath("$.items[1].expectedUnitReward").value(2_640))
                .andExpect(jsonPath("$.items[1].brandSupplyQuantity").value(150))
                // 상태 카드 — 고정 지급비 1,200,000원 · 공제 전 기준 · 실지급액은 계산하지 않는다
                .andExpect(jsonPath("$.fixedFee.amount").value(1_200_000))
                .andExpect(jsonPath("$.settlement.withholdingType").value("WITHHOLDING_3_3"))
                .andExpect(jsonPath("$.settlement.netAmount").doesNotExist())
                // 아직 종결·체결 전이다.
                .andExpect(jsonPath("$.closure.closedAt").doesNotExist())
                .andExpect(jsonPath("$.closure.actorType").doesNotExist())
                .andExpect(jsonPath("$.documents").isEmpty())
                .andExpect(jsonPath("$.groupBuy.groupBuyId").doesNotExist())
                .andExpect(jsonPath("$.groupBuy.awaitingBrandCreation").value(false))
                // 액션 — [서명 안내 다시 받기] [스레드에서 협의하기] [거절]
                .andExpect(jsonPath("$.permissions.canRequestResend").value(true))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true))
                .andExpect(jsonPath("$.permissions.canDecline").value(true))
                .andExpect(jsonPath("$.permissions.canDownloadDocuments").value(false))
                .andExpect(jsonPath("$.permissions.canOpenGroupBuy").value(false))
                // 서명 버튼은 없다 — 누를 대상(링크)이 없다(MVP · 모두싸인 API 미도입).
                .andExpect(jsonPath("$.permissions.canSign").doesNotExist())
                .andExpect(jsonPath("$.signature.signUrl").doesNotExist());
    }

    @Test
    @DisplayName("S3 — 조건을 고치거나 서명·취소·작성할 엔드포인트가 없다 — 화면 어디에도 조건 입력 필드가 없다")
    void s3HasNoWriteEndpointsBeyondDeclineAndResend() throws Exception {
        Long contractId = signingContract();
        String path = CONTRACTS + "/" + contractId;

        mockMvc.perform(put(path).header(HttpHeaders.AUTHORIZATION, myToken)
                        .contentType("application/json").content("{\"title\":\"바꾼 공구명\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post(path + "/sign").header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post(path + "/cancel").header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post(CONTRACTS).header(HttpHeaders.AUTHORIZATION, myToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());

        Contract untouched = load(contractId);
        assertThat(untouched.getStatus()).isEqualTo(ContractStatus.SIGNING);
        assertThat(untouched.getTitle()).isEqualTo("여름 수분 세럼 공구");
        assertThat(untouched.getCreatorSignedAt()).isNull();
    }

    // ── S3a · 브랜드 서명 완료 · 내 서명 필요 ──────────────────────────────

    @Test
    @DisplayName("S3a — 브랜드만 서명했으면 1/2이고 내 몫이 남았다 — 기준 시각은 운영자가 확인한 시각이다")
    void s3aBrandSignedMineRemaining() throws Exception {
        LocalDateTime brandSignedAt = LocalDateTime.now().minusHours(3).withNano(0);
        LocalDateTime checkedAt = LocalDateTime.now().minusHours(1).withNano(0);
        Long contractId = saveContract(ContractStatus.SIGNING, contract -> {
            approve(contract);
            contract.updateSignatures(brandSignedAt, null, checkedAt);
        });

        detail(contractId)
                .andExpect(jsonPath("$.status").value("SIGNING"))
                // 내 몫이 남았어도 배지 색은 정보색 그대로다 — 경고는 색이 아니라 배너·기한·주 버튼으로 표현한다.
                .andExpect(jsonPath("$.statusTone").value("INFO"))
                .andExpect(jsonPath("$.stepper.signedCount").value(1))
                .andExpect(jsonPath("$.signature.brandSignedAt").value(iso(brandSignedAt)))
                .andExpect(jsonPath("$.signature.creatorSignedAt").doesNotExist())
                // 「운영자 확인 · … 기준」 — 방금 서명한 사람이 「왜 반영이 안 됐지」로 오해하지 않게 한다.
                .andExpect(jsonPath("$.signature.asOf").value(iso(checkedAt)))
                .andExpect(jsonPath("$.permissions.canDecline").value(true))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(true))
                .andExpect(jsonPath("$.payout.fixedFeeAmount").value(1_200_000));

        // 순서가 아니라 각자의 몫 — S3에서도 S3a에서도 배지에 들어간다.
        summary().andExpect(jsonPath("$.actionRequiredCount").value(1));
    }

    // ── S3b · 내 서명 완료 · 브랜드 대기 (+ S11의 전제) ────────────────────

    @Test
    @DisplayName("S3b — 내가 먼저 서명했으면 할 일이 없다 — 액션은 [스레드에서 협의하기] 하나이고 서명은 체결이 아니다")
    void s3bMySignedWaitingForBrand() throws Exception {
        LocalDateTime mySignedAt = LocalDateTime.now().minusHours(2).withNano(0);
        LocalDateTime deadlineAt = LocalDateTime.now().plusDays(6).withNano(0);
        Long contractId = saveContract(ContractStatus.SIGNING, contract -> {
            approve(contract, deadlineAt);
            contract.updateSignatures(null, mySignedAt, LocalDateTime.now());
        });

        detail(contractId)
                // S11 「아직 계약이 체결된 것은 아닙니다」 — 서명 진행중이 유지되고 체결·공구·문서가 없다.
                .andExpect(jsonPath("$.status").value("SIGNING"))
                .andExpect(jsonPath("$.statusTone").value("INFO"))
                .andExpect(jsonPath("$.stepper.signedCount").value(1))
                .andExpect(jsonPath("$.stepper.concludedAt").doesNotExist())
                .andExpect(jsonPath("$.signature.brandSignedAt").doesNotExist())
                .andExpect(jsonPath("$.signature.creatorSignedAt").value(iso(mySignedAt)))
                // 상태 카드 「브랜드 서명 기한」 — 같은 기한이 이제 브랜드 쪽 기한으로 읽힌다.
                .andExpect(jsonPath("$.signature.deadlineAt").value(iso(deadlineAt)))
                .andExpect(jsonPath("$.documents").isEmpty())
                .andExpect(jsonPath("$.groupBuy.awaitingBrandCreation").value(false))
                .andExpect(jsonPath("$.permissions.canDecline").value(false))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(false))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true));

        // 「서명한 뒤에는 거절하거나 되돌릴 수 없습니다」
        decline(contractId, "{\"reasonCode\":\"ETC\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_ALREADY_SIGNED"));
        requestResend(contractId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_RESEND_NOT_ALLOWED"));

        summary().andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    // ── S3c · 체결 처리 대기 ────────────────────────────────────────────────

    @Test
    @DisplayName("S3c — 양측 서명이 끝나도 운영자 체결 전이다 — 공구도 PDF도 아직 없고 내가 할 일도 없다")
    void s3cBothSignedAwaitingOperator() throws Exception {
        LocalDateTime brandSignedAt = LocalDateTime.now().minusHours(4).withNano(0);
        LocalDateTime mySignedAt = LocalDateTime.now().minusHours(2).withNano(0);
        Long contractId = saveContract(ContractStatus.CONCLUSION_PENDING, contract -> {
            applyScreenTerms(contract, "여름 수분 세럼 공구");
            approve(contract);
            contract.updateSignatures(brandSignedAt, mySignedAt, LocalDateTime.now());
        });
        // 운영자는 체결 처리 직전에 문서를 올린다 — 올라가 있어도 체결 전에는 발급된 게 아니다.
        registerDocument(contractId, ContractDocumentType.SIGNED_PDF, "계약서.pdf");
        registerDocument(contractId, ContractDocumentType.AUDIT_TRAIL, "감사추적.pdf");

        detail(contractId)
                .andExpect(jsonPath("$.status").value("CONCLUSION_PENDING"))
                .andExpect(jsonPath("$.statusLabel").value("체결 처리 대기"))
                .andExpect(jsonPath("$.statusTone").value("INFO"))
                .andExpect(jsonPath("$.stepper.signedCount").value(2))
                .andExpect(jsonPath("$.stepper.concludedAt").doesNotExist())
                .andExpect(jsonPath("$.signature.brandSignedAt").value(iso(brandSignedAt)))
                .andExpect(jsonPath("$.signature.creatorSignedAt").value(iso(mySignedAt)))
                // 「계약서 — 체결 처리 시 PDF 발급」
                .andExpect(jsonPath("$.documents").isEmpty())
                .andExpect(jsonPath("$.permissions.canDownloadDocuments").value(false))
                // 「아직 공구는 만들어지지 않았습니다」 — 브랜드 생성 대기조차 아니다.
                .andExpect(jsonPath("$.groupBuy.groupBuyId").doesNotExist())
                .andExpect(jsonPath("$.groupBuy.awaitingBrandCreation").value(false))
                // 금액 카드는 서명 진행중과 같게 남는다.
                .andExpect(jsonPath("$.payout.fixedFeeAmount").value(1_200_000))
                .andExpect(jsonPath("$.content.obligationAlive").value(true))
                // 「여기서 내가 할 일은 없습니다」
                .andExpect(jsonPath("$.permissions.canDecline").value(false))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(false))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true));

        document(contractId, ContractDocumentType.SIGNED_PDF)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_DOCUMENT_NOT_FOUND"));
        requestResend(contractId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_RESEND_NOT_ALLOWED"));
        list().andExpect(jsonPath("$.content[0].deadline.type").value("BOTH_SIGNED"));
        summary().andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    // ── S5 · 거절 확인 모달 ─────────────────────────────────────────────────

    @Test
    @DisplayName("S5 — 사유 구분 5종이 시안 문구 그대로 저장·전달된다")
    void s5AcceptsTheFiveReasonsOfTheModal() throws Exception {
        String[][] reasons = {
                {"CONDITION_RENEGOTIATION", "조건 재협의 필요"},
                {"SCHEDULE_MISMATCH", "일정이 맞지 않음"},
                {"NOT_FIT_SHOWROOM", "상품이 내 쇼룸과 맞지 않음"},
                {"CONTENT_BURDEN", "콘텐츠 의무가 과함"},
                {"ETC", "기타"}};

        for (String[] reason : reasons) {
            Long contractId = signingContract();
            decline(contractId, "{\"reasonCode\":\"%s\"}".formatted(reason[0]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.closure.reasonCode").value(reason[0]))
                    .andExpect(jsonPath("$.closure.reasonLabel").value(reason[1]));
        }
    }

    @Test
    @DisplayName("S5 — 사유 구분은 필수이고 브랜드의 취소 사유는 고를 수 없다 · 메모는 선택이고 1,000자까지다")
    void s5ValidatesTheModalInput() throws Exception {
        Long contractId = signingContract();

        decline(contractId, "{\"memo\":\"사유 없이 메모만\"}")
                .andExpect(status().isBadRequest());
        // 취소 사유 5종과 한 enum에 합치면 거절 모달에 브랜드용 선택지가 뜰 수 있다 — 별도 enum이다.
        decline(contractId, "{\"reasonCode\":\"%s\"}".formatted(ContractCloseReasonCode.OUT_OF_STOCK.name()))
                .andExpect(status().isBadRequest());
        decline(contractId, "{\"reasonCode\":\"ETC\",\"memo\":\"%s\"}".formatted("가".repeat(1001)))
                .andExpect(status().isBadRequest());

        // 어느 것도 계약을 건드리지 않았다.
        assertThat(load(contractId).getStatus()).isEqualTo(ContractStatus.SIGNING);

        // 공백 메모는 「메모 없음」이다 — 브랜드에게 빈 말풍선을 보내지 않는다.
        decline(contractId, "{\"reasonCode\":\"ETC\",\"memo\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closure.memo").doesNotExist());
    }

    @Test
    @DisplayName("S5 — 거절은 되돌릴 수 없다 — 두 번째 거절은 409이고 처음 사유가 남는다")
    void s5DeclineIsIrreversible() throws Exception {
        Long contractId = signingContract();
        decline(contractId, "{\"reasonCode\":\"CONDITION_RENEGOTIATION\",\"memo\":\"처음 사유\"}")
                .andExpect(status().isOk());

        decline(contractId, "{\"reasonCode\":\"ETC\",\"memo\":\"다시 거절\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_DECLINE_NOT_ALLOWED"));

        Contract declined = load(contractId);
        assertThat(declined.getCloseReasonCode()).isEqualTo("CONDITION_RENEGOTIATION");
        assertThat(declined.getCloseReasonMemo()).isEqualTo("처음 사유");
    }

    // ── S6 · 체결완료 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("S6 — 문서 카드 2장(서명 PDF · 감사추적인증서)을 각각 내려받을 수 있고 고정 지급비는 「지급 전」이다")
    void s6ConcludedShowsDocumentCardsAndNotYetPaid() throws Exception {
        LocalDateTime concludedAt = LocalDateTime.now().minusHours(1).withNano(0);
        Long contractId = saveContract(ContractStatus.CONCLUDED, contract -> {
            applyScreenTerms(contract, "여름 수분 세럼 공구");
            approve(contract);
            contract.updateSignatures(LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(3),
                    LocalDateTime.now().minusHours(2));
            contract.conclude(concludedAt);
        });
        registerDocument(contractId, ContractDocumentType.SIGNED_PDF, "서명 완료 계약서.pdf");
        registerDocument(contractId, ContractDocumentType.AUDIT_TRAIL, "감사추적인증서.pdf");

        detail(contractId)
                .andExpect(jsonPath("$.status").value("CONCLUDED"))
                .andExpect(jsonPath("$.statusLabel").value("체결완료"))
                .andExpect(jsonPath("$.statusTone").value("SUCCESS"))
                // 헤더 「… 체결」 · 스텝퍼 4단 완료
                .andExpect(jsonPath("$.stepper.concludedAt").value(iso(concludedAt)))
                .andExpect(jsonPath("$.stepper.signedCount").value(2))
                // 계약 문서 카드 2장 — 카드 순서는 아래 테스트가 따로 본다.
                .andExpect(jsonPath("$.documents[*].documentType").value(containsInAnyOrder("SIGNED_PDF", "AUDIT_TRAIL")))
                .andExpect(jsonPath("$.documents[*].documentTypeLabel")
                        .value(containsInAnyOrder("서명 완료 계약서", "감사 추적 인증서")))
                .andExpect(jsonPath("$.documents[?(@.documentType == 'SIGNED_PDF')].downloadUrl")
                        .value(contains("https://signed.test/SIGNED_PDF")))
                .andExpect(jsonPath("$.permissions.canDownloadDocuments").value(true))
                // 「고정 지급비 지급 전」 — 체결을 입금으로 오해하면 분쟁이 된다.
                .andExpect(jsonPath("$.fixedFee.paymentState").value("NOT_YET"))
                .andExpect(jsonPath("$.payout.fixedFeeAmount").value(1_200_000))
                .andExpect(jsonPath("$.payout.platformGuaranteed").value(false))
                // 「내가 해야 할 일 — 체결로 확정된 약속」
                .andExpect(jsonPath("$.content.obligationAlive").value(true))
                // 「연결된 공구 — 브랜드 생성 대기」 · [공구 관리 열기]는 공구가 생긴 뒤에야 이동할 곳이 있다.
                .andExpect(jsonPath("$.groupBuy.awaitingBrandCreation").value(true))
                .andExpect(jsonPath("$.permissions.canOpenGroupBuy").value(false))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true))
                .andExpect(jsonPath("$.permissions.canDecline").value(false))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(false));

        // 카드의 [다운로드] — 파일명 · 용량 · 등록 시각
        document(contractId, ContractDocumentType.SIGNED_PDF)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("SIGNED_PDF"))
                .andExpect(jsonPath("$.downloadUrl").value("https://signed.test/SIGNED_PDF"))
                .andExpect(jsonPath("$.originalName").value("서명 완료 계약서.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(1_200_000))
                .andExpect(jsonPath("$.uploadedAt").exists());
        document(contractId, ContractDocumentType.AUDIT_TRAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentTypeLabel").value("감사 추적 인증서"));

        list().andExpect(jsonPath("$.content[0].deadline.type").value("SIGNED"));
    }

    @Test
    @DisplayName("S6 — 문서 카드는 시안 순서대로 서명 완료 계약서 → 감사추적인증서다")
    void s6DocumentCardsFollowScreenOrder() throws Exception {
        Long contractId = concludedContract();
        registerDocument(contractId, ContractDocumentType.SIGNED_PDF, "서명 완료 계약서.pdf");
        registerDocument(contractId, ContractDocumentType.AUDIT_TRAIL, "감사추적인증서.pdf");

        // 계약의 원본이 먼저, 그 서명 이력이 다음이다 — 문자열 정렬(A < S)로 뒤집히면 안 된다.
        detail(contractId)
                .andExpect(jsonPath("$.documents[*].documentType").value(contains("SIGNED_PDF", "AUDIT_TRAIL")));
    }

    @Test
    @DisplayName("S6 — 브랜드가 공구를 만들면 [공구 관리 열기]로 이동할 수 있고 「브랜드 생성 대기」가 풀린다")
    void s6OpensGroupBuyOnceBrandCreatedIt() throws Exception {
        Long contractId = concludedContract();
        transactionTemplate.executeWithoutResult(tx -> contractRepository.assignGroupBuy(contractId, 777L));

        detail(contractId)
                .andExpect(jsonPath("$.groupBuy.groupBuyId").value(777))
                .andExpect(jsonPath("$.groupBuy.awaitingBrandCreation").value(false))
                .andExpect(jsonPath("$.permissions.canOpenGroupBuy").value(true));
    }

    @Test
    @DisplayName("S6 — 이력은 최신이 위이고 화이트리스트 밖(서명 저장 감사 기록·문서 업로드·재발송 처리)은 빠지며 「연결 성립」이 맨 아래다")
    void s6HistoryIsNewestFirstAndWhitelisted() throws Exception {
        Long contractId = concludedContract();
        Contract contract = load(contractId);
        LocalDateTime base = LocalDateTime.now().minusDays(2);
        saveHistory(contract, ContractEventType.SIGNATURE_SENT, ContractActorType.ADMIN, "운영자", null, base);
        saveHistory(contract, ContractEventType.BRAND_SIGNED, ContractActorType.SELLER, "퓨어랩", null, base.plusHours(1));
        saveHistory(contract, ContractEventType.SIGNATURE_UPDATED, ContractActorType.ADMIN, "운영자",
                "브랜드 서명: null → …", base.plusHours(1).plusMinutes(10));
        saveHistory(contract, ContractEventType.CREATOR_SIGNED, ContractActorType.CREATOR, "뷰티_소연", null, base.plusHours(2));
        saveHistory(contract, ContractEventType.BOTH_SIGNED_CONFIRMED, ContractActorType.ADMIN, "운영자", null, base.plusHours(3));
        saveHistory(contract, ContractEventType.SIGNATURE_UPDATED, ContractActorType.ADMIN, "운영자",
                "인플루언서 서명: null → …", base.plusHours(3));
        saveHistory(contract, ContractEventType.DOCUMENT_UPLOADED, ContractActorType.ADMIN, "운영자", null, base.plusHours(4));
        saveHistory(contract, ContractEventType.RESEND_HANDLED, ContractActorType.ADMIN, "운영자", null, base.plusHours(4));
        saveHistory(contract, ContractEventType.CONCLUDED, ContractActorType.ADMIN, "운영자", null, base.plusHours(5));
        saveHistory(contract, ContractEventType.FIXED_FEE_PAID, ContractActorType.SELLER, "퓨어랩", null, base.plusHours(6));

        List<String> expected = new ArrayList<>(List.of(
                "CONCLUDED", "BOTH_SIGNED_CONFIRMED", "CREATOR_SIGNED", "BRAND_SIGNED", "SIGNATURE_SENT"));
        expected.add(null); // 연결 성립 — contract_history가 아니라 Connection에서 합성한 줄

        detail(contractId)
                .andExpect(jsonPath("$.history[*].eventType").value(contains(expected.toArray())))
                .andExpect(jsonPath("$.history[*].actorType").value(contains(
                        "ADMIN", "ADMIN", "CREATOR", "SELLER", "ADMIN", "SELLER")))
                .andExpect(jsonPath("$.history[5].actorDisplayName").value("퓨어랩"))
                // 운영자의 변경 내역 원문은 스튜디오에 내리지 않는다.
                .andExpect(content().string(not(containsString("서명: null"))));
    }

    // ── S7 · 거절(내가 거절) ────────────────────────────────────────────────

    @Test
    @DisplayName("S7 — 내가 끝낸 계약이다 — 내가 입력한 사유가 카드로 남고 금액 카드는 사라지며 연결은 유지된다")
    void s7DeclinedByMe() throws Exception {
        Long contractId = saveContract(ContractStatus.SIGNING, contract -> {
            applyScreenTerms(contract, "봄 클렌저 공구");
            approve(contract);
        });
        String memo = "리워드율 10%는 촬영 분량 대비 부담이 커서요. 릴스 수량을 줄이거나 리워드율 조정이 가능하면 다시 검토하고 싶습니다.";

        decline(contractId, "{\"reasonCode\":\"CONDITION_RENEGOTIATION\",\"memo\":\"%s\"}".formatted(memo))
                .andExpect(status().isOk());

        detail(contractId)
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.statusLabel").value("거절"))
                // 파트너센터와 같은 상태값 · 같은 색(위험)
                .andExpect(jsonPath("$.statusTone").value("DANGER"))
                // 거절 사유 카드 — 「내가 입력한 사유 · 일시」
                .andExpect(jsonPath("$.closure.actorType").value("CREATOR"))
                .andExpect(jsonPath("$.closure.closedAt").exists())
                .andExpect(jsonPath("$.closure.reasonLabel").value("조건 재협의 필요"))
                .andExpect(jsonPath("$.closure.memo").value(memo))
                // 금액 카드는 없고, 상태 카드는 「지급 없음」, 계약 조건의 금액은 남는다.
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.fixedFee.paymentState").value("NONE"))
                .andExpect(jsonPath("$.fixedFee.amount").value(1_200_000))
                // 콘텐츠 의무 — 「계약이 성립하지 않아 효력 없음」 · 카드는 남는다.
                .andExpect(jsonPath("$.content.storyCount").value(3))
                .andExpect(jsonPath("$.content.obligationAlive").value(false))
                .andExpect(jsonPath("$.items.length()").value(2))
                // 「연결 상태 연결됨 유지」 · 회복 경로는 [스레드에서 협의하기] 하나
                .andExpect(jsonPath("$.brand.connected").value(true))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true))
                .andExpect(jsonPath("$.permissions.canDecline").value(false))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(false))
                .andExpect(jsonPath("$.permissions.canDownloadDocuments").value(false))
                // 이력 「내가 거절 · 조건 재협의 필요 · 뷰티_소연」
                .andExpect(jsonPath("$.history[0].eventType").value("DECLINED"))
                .andExpect(jsonPath("$.history[0].actorType").value("CREATOR"))
                .andExpect(jsonPath("$.history[0].actorDisplayName").value("뷰티_소연"))
                .andExpect(jsonPath("$.history[0].detail").value("조건 재협의 필요"));

        list("tab", "CLOSED")
                .andExpect(jsonPath("$.content[0].contractId").value(contractId))
                .andExpect(jsonPath("$.content[0].deadline.type").value("NONE"));
    }

    // ── S8 · 만료 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("S8 — 운영자가 만료 처리한 계약은 사유 카드가 없고 중립색이며 [스레드에서 협의하기]만 남는다")
    void s8ExpiredByOperator() throws Exception {
        LocalDateTime deadlineAt = LocalDateTime.now().minusDays(1).withNano(0);
        Long contractId = saveContract(ContractStatus.EXPIRED, contract -> {
            approve(contract, LocalDateTime.now().minusDays(8), deadlineAt);
            contract.expire(LocalDateTime.now());
        });

        detail(contractId)
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.statusLabel").value("만료"))
                // 아무도 거절하지 않았다 — 중립 종결
                .andExpect(jsonPath("$.statusTone").value("NEUTRAL"))
                // 종결 주체는 「시스템」이 아니라 「운영자」다(MVP · 자동 판정 아님).
                .andExpect(jsonPath("$.closure.actorType").value("ADMIN"))
                .andExpect(jsonPath("$.closure.closedAt").exists())
                .andExpect(jsonPath("$.closure.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.closure.reasonLabel").doesNotExist())
                .andExpect(jsonPath("$.closure.memo").doesNotExist())
                // 상태 카드 「서명 기한 …」은 남는다.
                .andExpect(jsonPath("$.signature.deadlineAt").value(iso(deadlineAt)))
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.fixedFee.paymentState").value("NONE"))
                .andExpect(jsonPath("$.content.obligationAlive").value(false))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true))
                .andExpect(jsonPath("$.permissions.canDecline").value(false))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(false));

        decline(contractId, "{\"reasonCode\":\"ETC\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_DECLINE_NOT_ALLOWED"));
        // 재발송은 브랜드만 할 수 있다 — 스튜디오는 스레드에서 「요청」한다.
        requestResend(contractId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_RESEND_NOT_ALLOWED"));
        list().andExpect(jsonPath("$.content[0].deadline.type").value("PASSED"));
    }

    // ── S9 · 취소 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("S9 — 도착한 계약의 취소는 운영자가 한다 — 운영자가 입력한 사유·메모가 카드로 오고 내가 할 조치가 없다")
    void s9CanceledByOperator() throws Exception {
        Long contractId = canceledContract();

        detail(contractId)
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.statusLabel").value("취소"))
                .andExpect(jsonPath("$.statusTone").value("NEUTRAL"))
                // 서명 요청 발송 이후라 브랜드는 취소할 수 없다 — 모두싸인 요청을 거둔 운영자가 주체다.
                .andExpect(jsonPath("$.closure.actorType").value("ADMIN"))
                .andExpect(jsonPath("$.closure.reasonCode").value("SCHEDULE_CHANGE"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("공구 일정 변경"))
                .andExpect(jsonPath("$.closure.memo").value("생산 일정이 밀려 공구 기간을 다시 잡아야 합니다."))
                .andExpect(jsonPath("$.closure.closedAt").exists())
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.fixedFee.paymentState").value("NONE"))
                .andExpect(jsonPath("$.content.obligationAlive").value(false))
                .andExpect(jsonPath("$.brand.connected").value(true))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true))
                .andExpect(jsonPath("$.permissions.canDecline").value(false))
                .andExpect(jsonPath("$.permissions.canRequestResend").value(false));

        decline(contractId, "{\"reasonCode\":\"ETC\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_DECLINE_NOT_ALLOWED"));
        list().andExpect(jsonPath("$.content[0].deadline.type").value("NONE"));
    }

    // ── 종결 3종 공통 · 회복 경로 ───────────────────────────────────────────

    @Test
    @DisplayName("종결 후 연결이 끊겼으면 [스레드에서 협의하기]도 닫힌다 — 「연결 상태」 표시가 그 판정이다")
    void closesThreadWhenConnectionIsGone() throws Exception {
        Long contractId = declinedContract();
        transactionTemplate.executeWithoutResult(tx -> {
            Connection connection = connectionRepository.findById(myConnection.getId()).orElseThrow();
            connection.markDisconnected();
        });

        detail(contractId)
                .andExpect(jsonPath("$.brand.connected").value(false))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(false));
    }

    @Test
    @DisplayName("스레드가 아직 없으면 threadId는 null이고 [스레드 열기]·분쟁 경로를 지어내지 않는다")
    void doesNotInventThreadWhenNoneExists() throws Exception {
        Market other = otherBrand("○○ 브랜드");
        Connection connection = Connection.requestPair(other, me);
        connection.markConnected();
        connectionRepository.save(connection);
        Long contractId = saveContractFor(other, me, connection, ContractStatus.SIGNING, this::approve);

        detail(contractId)
                .andExpect(jsonPath("$.brand.name").value("○○ 브랜드"))
                .andExpect(jsonPath("$.brand.connected").value(true))
                .andExpect(jsonPath("$.brand.threadId").doesNotExist())
                .andExpect(jsonPath("$.payout.disputeChannel.threadId").doesNotExist())
                .andExpect(jsonPath("$.permissions.canOpenThread").value(false));
    }

    @Test
    @DisplayName("목록에서 상대를 고른 계약(connection_id 없음)도 지금의 연결로 스레드를 찾아 준다")
    void resolvesThreadForContractsStartedFromList() throws Exception {
        Long contractId = saveContractFor(brand.market(), me, null, ContractStatus.SIGNING, this::approve);

        detail(contractId)
                .andExpect(jsonPath("$.brand.threadId").value(myThread.getId()))
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true));
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    /**
     * 시안 S1의 8행을 그 순서대로 받는다 — 위 행일수록 최근에 받았다(기본 정렬 = 받은 순).
     * 브랜드 넷 중 퓨어랩만 내 연결·스레드가 있는 브랜드다.
     */
    private void seedS1Rows(LocalDateTime imminentDeadline) {
        Market oo = otherBrand("○○ 브랜드");
        Market tri = otherBrand("△△ 코스메틱");
        Market sq = otherBrand("□□ 뷰티랩");
        Market pure = brand.market();
        LocalDateTime now = LocalDateTime.now();

        receive(oo, "여름 수분 세럼 공구", 2, 1, imminentDeadline, ContractStatus.SIGNING, c -> { });
        receive(tri, "가을 앰플 신제품 공구", 1, 2, now.plusDays(8), ContractStatus.SIGNING,
                c -> c.updateSignatures(null, now.minusMinutes(30), now));
        receive(pure, "퓨어랩 립밤 재입고 공구", 2, 3, now.plusDays(8), ContractStatus.CONCLUSION_PENDING,
                c -> c.updateSignatures(now.minusMinutes(90), now.minusMinutes(60), now));
        receive(tri, "글로우 크림 앵콜 공구", 1, 4, now.plusDays(8), ContractStatus.CONCLUDED, c -> {
            c.updateSignatures(now.minusMinutes(90), now.minusMinutes(60), now);
            c.conclude(now);
        });
        receive(sq, "여름 미스트 기획 공구", 1, 5, now.plusDays(8), ContractStatus.CONCLUDED, c -> {
            c.updateSignatures(now.minusMinutes(90), now.minusMinutes(60), now);
            c.conclude(now);
        });
        Long spring = receive(sq, "봄 클렌저 공구", 1, 6, now.plusDays(8), ContractStatus.SIGNING, c -> { });
        declined(spring, ContractDeclineReason.CONDITION_RENEGOTIATION, "리워드율 조정이 가능하면 다시 검토하고 싶습니다.");
        receive(oo, "수분 토너 리뉴얼 공구", 1, 7, now.minusHours(1), ContractStatus.EXPIRED, c -> c.expire(now));
        receive(tri, "클렌징 오일 여름 공구", 1, 8, now.plusDays(8), ContractStatus.CANCELED,
                c -> c.applyCanceledByAdmin(ContractCloseReasonCode.SCHEDULE_CHANGE.name(), null, now));

        // 아직 도착하지 않은 계약 — 8건에 섞이면 안 된다.
        saveContract(ContractStatus.DRAFT, c -> { });
    }

    private Long receive(Market market, String title, int itemCount, int receivedHoursAgo,
                         LocalDateTime deadlineAt, ContractStatus status, Consumer<Contract> then) {
        Connection connection = market.getId().equals(brand.market().getId()) ? myConnection : null;
        return saveContractFor(market, me, connection, status, contract -> {
            applyScreenTerms(contract, title);
            if (itemCount == 1) {
                contract.replaceItems(new ArrayList<>(List.of(item(serum, 28_000, "15.0", 300))));
            }
            approve(contract, LocalDateTime.now().minusHours(receivedHoursAgo), deadlineAt);
            then.accept(contract);
        });
    }
}
