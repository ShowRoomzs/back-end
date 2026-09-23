package showroomz.api.creator.contract;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.api.admin.contract.dto.AdminContractDto.ApproveRequest;
import showroomz.api.admin.contract.dto.AdminContractDto.DownloadResponse;
import showroomz.api.admin.contract.dto.AdminContractDto.RegisterDocumentRequest;
import showroomz.api.admin.contract.dto.AdminContractDto.RejectRequest;
import showroomz.api.admin.contract.dto.AdminContractDto.SignatureRequest;
import showroomz.api.admin.contract.service.ContractDocumentStorage;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractReviewRejectReason;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.FixedFeeTrigger;
import showroomz.domain.contract.type.SecondaryUsePeriodType;
import showroomz.domain.member.seller.entity.Seller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시안 상단의 확정 절차를 <b>실제 API로</b> 한 번 끝까지 태운다 —
 * 브랜드 작성 → 운영자 검토 → 양측 동시 서명 요청 → 브랜드 · 나 각각 서명 → 운영자 체결 완료 처리 → 체결완료
 * (└ 내 거절 / 만료 / 브랜드 취소).
 *
 * <p>다른 스튜디오 테스트는 상태를 직접 적재한다. 그 방식으로는 <b>다른 서피스가 실제로 남기는 값</b>
 * (이력 이벤트 · 표시명 · 기준 시각)이 스튜디오 화면이 기대하는 것과 맞는지 확인할 수 없다.
 * 여기서는 브랜드·운영자 API가 쓴 행을 스튜디오 API가 읽는다.
 */
@DisplayName("[통합] 쇼룸 스튜디오 계약 — 브랜드·운영자와 잇는 흐름")
class CreatorContractLifecycleIntegrationTest extends CreatorContractTestSupport {

    private static final String SELLER_CONTRACTS = "/v1/seller/contracts";
    private static final String ADMIN_CONTRACTS = "/v1/admin/contracts";
    private static final String OPERATOR_NAME = "김운영";

    @MockitoBean
    private ContractDocumentStorage contractDocumentStorage;

    private Seller operator;
    private String operatorToken;
    private String brandToken;

    @BeforeEach
    void setUpOtherSurfaces() {
        operator = fixture.createAdmin("ops@showroomz.test", OPERATOR_NAME);
        operatorToken = adminToken(operator);
        brandToken = sellerToken(brand.seller());

        when(contractDocumentStorage.sealUpload(anyString(), anyLong(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(contractDocumentStorage.download(any())).thenAnswer(invocation -> {
            ContractDocument document = invocation.getArgument(0);
            return new DownloadResponse(
                    "https://signed.test/" + document.getDocumentType(),
                    document.getOriginalName(), document.getSizeBytes(), 300, null);
        });
    }

    // ── 정상 흐름 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("브랜드 작성·운영자 검토 중에는 보이지 않다가 검토 통과 순간 서명 진행중으로 도착하고, 각자 서명 → 체결까지 화면이 따라간다")
    void followsTheWholeProcedure() throws Exception {
        long contractId = brandWritesContractForMe();
        // 작성중 — 내 creator_id가 이미 박혀 있지만 도착 전이다.
        detail(contractId).andExpect(status().isNotFound());

        brandRequestsReview(contractId);
        // 검토 대기 — 운영자 검토를 통과해야 서명 요청이 온다.
        detail(contractId).andExpect(status().isNotFound());
        summary().andExpect(jsonPath("$.tabCounts.ALL").value(0));

        LocalDateTime sentAt = LocalDateTime.now().minusHours(1).withNano(0);
        LocalDateTime deadlineAt = LocalDateTime.now().plusDays(8).withNano(0);
        operatorApproves(contractId, sentAt, deadlineAt);

        // S3 — 양측 동시 서명 요청 · 0/2 · 기준 시각 = 발송 시각
        detail(contractId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNING"))
                .andExpect(jsonPath("$.receivedAt").value(iso(sentAt)))
                .andExpect(jsonPath("$.stepper.reviewApprovedAt").exists())
                .andExpect(jsonPath("$.stepper.signatureRequestedAt").value(iso(sentAt)))
                .andExpect(jsonPath("$.stepper.signedCount").value(0))
                .andExpect(jsonPath("$.signature.deadlineAt").value(iso(deadlineAt)))
                .andExpect(jsonPath("$.signature.asOf").value(iso(sentAt)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].expectedUnitReward").value(4_200))
                .andExpect(jsonPath("$.items[1].expectedUnitReward").value(2_640))
                .andExpect(jsonPath("$.payout.fixedFeeAmount").value(1_200_000))
                .andExpect(jsonPath("$.content.preReview").value(true))
                .andExpect(jsonPath("$.brand.threadId").value(myThread.getId()))
                .andExpect(jsonPath("$.permissions.canDecline").value(true));
        summary()
                .andExpect(jsonPath("$.tabCounts.SIGNING").value(1))
                .andExpect(jsonPath("$.actionRequiredCount").value(1));

        // S3a — 브랜드가 먼저 서명 · 운영자가 모두싸인에서 확인해 입력
        LocalDateTime brandSignedAt = LocalDateTime.now().minusMinutes(40).withNano(0);
        operatorRecordsSignatures(contractId, brandSignedAt, null);
        detail(contractId)
                .andExpect(jsonPath("$.status").value("SIGNING"))
                .andExpect(jsonPath("$.stepper.signedCount").value(1))
                .andExpect(jsonPath("$.signature.brandSignedAt").value(iso(brandSignedAt)))
                .andExpect(jsonPath("$.signature.creatorSignedAt").doesNotExist())
                // 기준 시각이 운영자 확인 시각으로 넘어갔다 — 발송 시각에 머물러 있으면 안 된다.
                .andExpect(jsonPath("$.signature.asOf").value(not(iso(sentAt))))
                .andExpect(jsonPath("$.permissions.canDecline").value(true));
        summary().andExpect(jsonPath("$.actionRequiredCount").value(1));

        // S3c — 내 서명까지 확인되면 운영자 체결 처리 대기
        LocalDateTime mySignedAt = LocalDateTime.now().minusMinutes(20).withNano(0);
        operatorRecordsSignatures(contractId, brandSignedAt, mySignedAt);
        detail(contractId)
                .andExpect(jsonPath("$.status").value("CONCLUSION_PENDING"))
                .andExpect(jsonPath("$.stepper.signedCount").value(2))
                .andExpect(jsonPath("$.signature.creatorSignedAt").value(iso(mySignedAt)))
                .andExpect(jsonPath("$.permissions.canDecline").value(false));
        summary()
                .andExpect(jsonPath("$.tabCounts.SIGNING").value(0))
                .andExpect(jsonPath("$.tabCounts.CONCLUSION_PENDING").value(1))
                .andExpect(jsonPath("$.actionRequiredCount").value(0));

        // 운영자가 모두싸인에서 받은 두 파일을 올린다 — 체결 처리 전에는 아직 「발급」 전이다.
        operatorUploads(contractId, ContractDocumentType.SIGNED_PDF, "서명 완료 계약서.pdf");
        operatorUploads(contractId, ContractDocumentType.AUDIT_TRAIL, "감사추적인증서.pdf");
        detail(contractId).andExpect(jsonPath("$.documents").isEmpty());
        document(contractId, ContractDocumentType.SIGNED_PDF).andExpect(status().isNotFound());

        // S6 — 운영자 체결 완료 처리
        operatorConcludes(contractId);
        detail(contractId)
                .andExpect(jsonPath("$.status").value("CONCLUDED"))
                .andExpect(jsonPath("$.statusTone").value("SUCCESS"))
                .andExpect(jsonPath("$.stepper.concludedAt").exists())
                // 카드 순서는 화면 대조(S6)가 본다 — 여기서는 두 장이 발급됐는지만.
                .andExpect(jsonPath("$.documents[*].documentType").value(containsInAnyOrder("SIGNED_PDF", "AUDIT_TRAIL")))
                .andExpect(jsonPath("$.permissions.canDownloadDocuments").value(true))
                .andExpect(jsonPath("$.fixedFee.paymentState").value("NOT_YET"))
                .andExpect(jsonPath("$.groupBuy.awaitingBrandCreation").value(true));
        document(contractId, ContractDocumentType.SIGNED_PDF)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value(containsString("서명 완료 계약서.pdf")));
        summary().andExpect(jsonPath("$.tabCounts.CONCLUDED").value(1));
    }

    @Test
    @DisplayName("브랜드가 [지급 완료 기록]을 눌러도 스튜디오는 「지급 완료」가 아니라 「브랜드 기록」으로 받는다 — 이력에도 싣지 않는다")
    void brandPaymentRecordIsNotShownAsPaid() throws Exception {
        long contractId = concludedThroughApis();

        mockMvc.perform(post(SELLER_CONTRACTS + "/" + contractId + "/fixed-fee/payment")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk());

        detail(contractId)
                .andExpect(jsonPath("$.fixedFee.paymentState").value("RECORDED_BY_BRAND"))
                .andExpect(jsonPath("$.fixedFee.paidAt").doesNotExist())
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("FIXED_FEE_PAID"))));
    }

    // ── 이력 · 표시명 — 다른 서피스가 남긴 값이 시안과 맞는가 ─────────────

    @Test
    @DisplayName("S3a 이력에는 「브랜드 서명 완료」가 서고, 체결완료(S6)까지 시안의 5줄이 순서대로 쌓인다")
    void signatureHistoryMatchesScreen() throws Exception {
        long contractId = arrivedAtMe();
        LocalDateTime brandSignedAt = LocalDateTime.now().minusMinutes(40).withNano(0);
        LocalDateTime mySignedAt = LocalDateTime.now().minusMinutes(20).withNano(0);

        operatorRecordsSignatures(contractId, brandSignedAt, null);
        // S3a — 브랜드 서명 완료 · 서명 요청 도착 · 연결 성립
        // 「양측 서명 완료 확인」이 여기 끼면 한쪽만 서명한 계약에 거짓 문구가 선다.
        detail(contractId)
                .andExpect(jsonPath("$.history[*].eventType").value(contains("BRAND_SIGNED", "SIGNATURE_SENT", null)))
                .andExpect(jsonPath("$.history[0].actorType").value("SELLER"))
                .andExpect(jsonPath("$.history[0].actorDisplayName").value("퓨어랩"))
                .andExpect(jsonPath("$.history[0].occurredAt").value(iso(brandSignedAt)))
                // 운영자가 남긴 변경 내역 원문은 스튜디오에 내리지 않는다.
                .andExpect(content().string(not(containsString("브랜드 서명:"))));

        operatorRecordsSignatures(contractId, brandSignedAt, mySignedAt);
        // S3c — 양측 서명 완료 확인 · 내 서명 완료 · 브랜드 서명 완료 · 서명 요청 도착 · 연결 성립
        detail(contractId)
                .andExpect(jsonPath("$.history[*].eventType").value(contains(
                        "BOTH_SIGNED_CONFIRMED", "CREATOR_SIGNED", "BRAND_SIGNED", "SIGNATURE_SENT", null)))
                .andExpect(jsonPath("$.history[1].actorDisplayName").value("뷰티_소연"))
                .andExpect(content().string(not(containsString("인플루언서 서명:"))));

        operatorUploads(contractId, ContractDocumentType.SIGNED_PDF, "계약서.pdf");
        operatorUploads(contractId, ContractDocumentType.AUDIT_TRAIL, "감사추적.pdf");
        operatorConcludes(contractId);

        // S6 — 체결완료 · 양측 서명 완료 확인 · 내 서명 완료 · 브랜드 서명 완료 · 서명 요청 도착 (· 연결 성립)
        String body = detail(contractId).andReturn().getResponse().getContentAsString();
        List<String> events = JsonPath.parse(body).read("$.history[*].eventType");
        assertThat(events).startsWith(
                "CONCLUDED", "BOTH_SIGNED_CONFIRMED", "CREATOR_SIGNED", "BRAND_SIGNED", "SIGNATURE_SENT");
    }

    @Test
    @DisplayName("S3b 이력 — 내가 먼저 서명했으면 「내 서명 완료」가 서고 양측 확인은 아직 없다")
    void mySignatureFirstShowsOnlyMine() throws Exception {
        long contractId = arrivedAtMe();
        LocalDateTime mySignedAt = LocalDateTime.now().minusMinutes(20).withNano(0);

        operatorRecordsSignatures(contractId, null, mySignedAt);

        detail(contractId)
                .andExpect(jsonPath("$.history[*].eventType").value(contains("CREATOR_SIGNED", "SIGNATURE_SENT", null)))
                .andExpect(jsonPath("$.history[0].actorType").value("CREATOR"))
                .andExpect(jsonPath("$.history[0].actorDisplayName").value("뷰티_소연"))
                .andExpect(jsonPath("$.history[0].occurredAt").value(iso(mySignedAt)));
    }

    @Test
    @DisplayName("스튜디오 응답에 운영자 실명이 실리지 않는다 — 화면 문구는 「운영자」로 통일한다")
    void doesNotExposeOperatorNameToCreator() throws Exception {
        long contractId = arrivedAtMe();
        operatorRecordsSignatures(contractId,
                LocalDateTime.now().minusMinutes(40).withNano(0), LocalDateTime.now().minusMinutes(20).withNano(0));

        // 화면에 안 그려도 JSON에 실리면 이미 유출이다(설계서 0-4).
        detail(contractId)
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(OPERATOR_NAME))));
    }

    @Test
    @DisplayName("검토 반려를 거쳐 도착한 계약에도 반려 사유·검토 이력이 실리지 않는다 — 운영자가 브랜드에게만 한 지적이다")
    void doesNotLeakReviewRejectionAfterArrival() throws Exception {
        String rejectDetail = "공구가가 정가보다 높게 입력되었습니다 — 상품 정보를 확인해 주세요.";
        long contractId = brandWritesContractForMe();
        brandRequestsReview(contractId);
        mockMvc.perform(post(ADMIN_CONTRACTS + "/" + contractId + "/review/reject")
                        .header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RejectRequest(ContractReviewRejectReason.INFO_MISMATCH, rejectDetail))))
                .andExpect(status().isOk());

        // 검토 반려 — 「나한테 보내려다 운영자에게 막혔다」가 읽히면 안 된다.
        detail(contractId).andExpect(status().isNotFound());

        brandRequestsReview(contractId);
        operatorApproves(contractId, LocalDateTime.now().minusMinutes(30).withNano(0),
                LocalDateTime.now().plusDays(8).withNano(0));

        detail(contractId)
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(rejectDetail))))
                .andExpect(content().string(not(containsString("INFO_MISMATCH"))))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("REVIEW_REJECTED"))))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("REVIEW_REQUESTED"))))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("CREATED"))));
    }

    // ── 종결 3종 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("S7 — 내가 거절하면 사유·메모가 브랜드 화면에 그대로 전달되고 연결은 유지된다")
    void declineReachesBrandAsIs() throws Exception {
        long contractId = arrivedAtMe();
        String memo = "리워드율 조정이 가능하면 다시 검토하고 싶습니다.";

        decline(contractId, "{\"reasonCode\":\"CONDITION_RENEGOTIATION\",\"memo\":\"%s\"}".formatted(memo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mockMvc.perform(get(SELLER_CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.closure.actorType").value("CREATOR"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("조건 재협의 필요"))
                .andExpect(jsonPath("$.closure.memo").value(memo));

        // 「연결은 유지되므로 브랜드가 조건을 고쳐 새 계약을 다시 보낼 수 있습니다」
        long next = brandWritesContractForMe();
        brandRequestsReview(next);
        operatorApproves(next, LocalDateTime.now().minusMinutes(10).withNano(0),
                LocalDateTime.now().plusDays(8).withNano(0));
        summary()
                .andExpect(jsonPath("$.tabCounts.SIGNING").value(1))
                .andExpect(jsonPath("$.tabCounts.CLOSED").value(1));
    }

    @Test
    @DisplayName("S8 — 기한이 지나도 스스로 닫히지 않고, 운영자가 만료 처리해야 만료가 된다")
    void expiryIsManualByOperator() throws Exception {
        long contractId = brandWritesContractForMe();
        brandRequestsReview(contractId);
        LocalDateTime passedDeadline = LocalDateTime.now().minusMinutes(30).withNano(0);
        operatorApproves(contractId, LocalDateTime.now().minusMinutes(90).withNano(0), passedDeadline);

        // 기한 경과 · 운영자 확인 전 — 여전히 서명 진행중이고 경고색 날짜다.
        list()
                .andExpect(jsonPath("$.content[0].status").value("SIGNING"))
                .andExpect(jsonPath("$.content[0].deadline.type").value("DEADLINE"))
                .andExpect(jsonPath("$.content[0].deadline.tone").value("WARNING"));

        mockMvc.perform(post(ADMIN_CONTRACTS + "/" + contractId + "/expire")
                        .header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dashboardRechecked\":true}"))
                .andExpect(status().isOk());

        detail(contractId)
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.statusTone").value("NEUTRAL"))
                .andExpect(jsonPath("$.closure.actorType").value("ADMIN"))
                .andExpect(jsonPath("$.closure.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.permissions.canOpenThread").value(true))
                .andExpect(jsonPath("$.history[0].eventType").value("EXPIRED"))
                .andExpect(jsonPath("$.history[0].actorType").value("ADMIN"));
        list().andExpect(jsonPath("$.content[0].deadline.type").value("PASSED"));
    }

    @Test
    @DisplayName("S9 — 브랜드가 철회하면 브랜드가 입력한 사유가 오고 배지에서 빠진다")
    void brandCancellationArrivesAsCanceled() throws Exception {
        long contractId = arrivedAtMe();
        summary().andExpect(jsonPath("$.actionRequiredCount").value(1));

        String memo = "생산 일정이 밀려 공구 기간을 다시 잡아야 합니다.";
        mockMvc.perform(post(SELLER_CONTRACTS + "/" + contractId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"SCHEDULE_CHANGE\",\"memo\":\"%s\"}".formatted(memo)))
                .andExpect(status().isOk());

        detail(contractId)
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.closure.actorType").value("SELLER"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("공구 일정 변경"))
                .andExpect(jsonPath("$.closure.memo").value(memo))
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.permissions.canDecline").value(false))
                // 이력 「브랜드 철회 · 취소 · △△ 코스메틱」
                .andExpect(jsonPath("$.history[0].eventType").value("CANCELED"))
                .andExpect(jsonPath("$.history[0].actorType").value("SELLER"))
                .andExpect(jsonPath("$.history[0].actorDisplayName").value("퓨어랩"));
        summary()
                .andExpect(jsonPath("$.tabCounts.CLOSED").value(1))
                .andExpect(jsonPath("$.actionRequiredCount").value(0));
    }

    // ── 서명 안내 다시 받기 · 열람 기록 ─────────────────────────────────────

    @Test
    @DisplayName("[서명 안내 다시 받기]는 운영자 큐에 한 줄로 쌓이고, 운영자가 처리한 뒤에는 다시 요청할 수 있다")
    void resendRequestGoesThroughOperatorQueue() throws Exception {
        long contractId = arrivedAtMe();

        requestResend(contractId).andExpect(jsonPath("$.alreadyRequested").value(false));
        requestResend(contractId).andExpect(jsonPath("$.alreadyRequested").value(true));
        mockMvc.perform(get(ADMIN_CONTRACTS + "/summary").header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(jsonPath("$.queues.RESEND").value(1));

        mockMvc.perform(post(ADMIN_CONTRACTS + "/" + contractId + "/resend/handle")
                        .header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isOk());

        // 재발송이 처리됐어도 링크를 또 못 찾을 수 있다 — 새 요청이 새 줄로 들어간다.
        requestResend(contractId).andExpect(jsonPath("$.alreadyRequested").value(false));
        assertThat(resendRequestRepository.findByContractIdOrderByRequestedAtDescIdDesc(contractId)).hasSize(2);

        // 요청·처리는 운영자와의 일이다 — 스튜디오 이력 카드에 싣지 않는다.
        detail(contractId)
                .andExpect(jsonPath("$.status").value("SIGNING"))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("RESEND_REQUESTED"))))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("RESEND_HANDLED"))));
    }

    @Test
    @DisplayName("상세를 처음 열면 브랜드 화면의 「상대 열람」이 켜진다 — 이력에는 남지 않는다")
    void firstViewReachesBrandAsViewed() throws Exception {
        long contractId = arrivedAtMe();

        brandDetail(contractId).andExpect(jsonPath("$.signature.counterpartyViewed").value(false));

        detail(contractId).andExpect(status().isOk());

        brandDetail(contractId)
                .andExpect(jsonPath("$.signature.counterpartyViewed").value(true))
                .andExpect(jsonPath("$.history[*].eventType", not(hasItem("VIEWED"))));
    }

    // ── 브랜드 · 운영자 조작 ────────────────────────────────────────────────

    /** 브랜드가 스레드에서 나를 골라 시안 S3의 조건으로 작성한다(작성중). */
    private long brandWritesContractForMe() throws Exception {
        String created = mockMvc.perform(post(SELLER_CONTRACTS)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\":%d}".formatted(me.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long contractId = JsonPath.parse(created).read("$.contractId", Number.class).longValue();

        mockMvc.perform(put(SELLER_CONTRACTS + "/" + contractId)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(screenForm(load(contractId).getVersion()))))
                .andExpect(status().isOk());
        return contractId;
    }

    private ContractUpdateRequest screenForm(long version) {
        return new ContractUpdateRequest(
                version, me.getId(), "여름 수분 세럼 공구", screenStartAt(), screenEndAt(),
                1_200_000, FixedFeeTrigger.POST_REGISTERED, true,
                1, 1, 3, screenEndAt().toLocalDate(),
                true, SecondaryUsePeriodType.FIXED, 12,
                true, "2차 활용 범위: 자사 상세페이지·인스타 광고 소재로 게시 후 12개월간 사용.",
                List.of(
                        new ContractUpdateRequest.Item(null, serum.getProductId(), 28_000, new BigDecimal("15.0"), 300),
                        new ContractUpdateRequest.Item(null, cream.getProductId(), 22_000, new BigDecimal("12.0"), 150)));
    }

    /**
     * 검토 요청 — 고정 지급비 1,200,000원은 W6(100만 원 초과) 경고라 확인을 함께 보낸다.
     * 요청 시각을 조금 앞당겨 둔다: 발송 일시는 검토 요청 이후여야 하는데 응답 직렬화가 초 단위라
     * 같은 초 안에서는 경계가 흔들린다.
     */
    private void brandRequestsReview(long contractId) throws Exception {
        mockMvc.perform(post(SELLER_CONTRACTS + "/" + contractId + "/review-request")
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgedWarnings\":[\"W6\"]}"))
                .andExpect(status().isOk());
        transactionTemplate.executeWithoutResult(tx -> {
            Contract contract = load(contractId);
            contract.applyReviewRequested(contract.getContractNumber(), contract.getClauseVersion(),
                    contract.getWarningFlags(), LocalDateTime.now().minusHours(3));
            contractRepository.save(contract);
        });
    }

    private void operatorApproves(long contractId, LocalDateTime sentAt, LocalDateTime deadlineAt) throws Exception {
        mockMvc.perform(post(ADMIN_CONTRACTS + "/" + contractId + "/review/approve")
                        .header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApproveRequest(true, true, true, sentAt, deadlineAt))))
                .andExpect(status().isOk());
    }

    private void operatorRecordsSignatures(long contractId, LocalDateTime brandSignedAt,
                                           LocalDateTime creatorSignedAt) throws Exception {
        mockMvc.perform(put(ADMIN_CONTRACTS + "/" + contractId + "/signatures")
                        .header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new SignatureRequest(
                                brandSignedAt, creatorSignedAt, load(contractId).getVersion()))))
                .andExpect(status().isOk());
    }

    private void operatorUploads(long contractId, ContractDocumentType type, String fileName) throws Exception {
        String s3Key = "contracts/%d/uploads/%d/%s/%s.pdf".formatted(contractId, operator.getId(), type, UUID.randomUUID());
        mockMvc.perform(post(ADMIN_CONTRACTS + "/" + contractId + "/documents")
                        .header(HttpHeaders.AUTHORIZATION, operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RegisterDocumentRequest(type, s3Key, fileName, 1_200_000L))))
                .andExpect(status().isOk());
    }

    private void operatorConcludes(long contractId) throws Exception {
        mockMvc.perform(post(ADMIN_CONTRACTS + "/" + contractId + "/conclude")
                        .header(HttpHeaders.AUTHORIZATION, operatorToken))
                .andExpect(status().isOk());
    }

    private ResultActions brandDetail(long contractId) throws Exception {
        return mockMvc.perform(get(SELLER_CONTRACTS + "/" + contractId).header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    /** 작성 → 검토 요청 → 검토 통과(서명 요청 발송)까지 — 스튜디오에 도착한 시점이다. */
    private long arrivedAtMe() throws Exception {
        long contractId = brandWritesContractForMe();
        brandRequestsReview(contractId);
        operatorApproves(contractId, LocalDateTime.now().minusHours(1).withNano(0),
                LocalDateTime.now().plusDays(8).withNano(0));
        assertThat(load(contractId).getStatus()).isEqualTo(ContractStatus.SIGNING);
        return contractId;
    }

    private long concludedThroughApis() throws Exception {
        long contractId = arrivedAtMe();
        operatorRecordsSignatures(contractId,
                LocalDateTime.now().minusMinutes(40).withNano(0), LocalDateTime.now().minusMinutes(20).withNano(0));
        operatorUploads(contractId, ContractDocumentType.SIGNED_PDF, "계약서.pdf");
        operatorUploads(contractId, ContractDocumentType.AUDIT_TRAIL, "감사추적.pdf");
        operatorConcludes(contractId);
        return contractId;
    }
}
