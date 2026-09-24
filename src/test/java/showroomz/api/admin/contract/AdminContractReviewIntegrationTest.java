package showroomz.api.admin.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.admin.contract.dto.AdminContractDto.ApproveRequest;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.type.*;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시안 B1·B1-b(검토 대기 상세) · B2(반려 완료) · C1(검토 승인 모달) · C2(검토 반려 모달).
 *
 * <p>「승인 ≠ 발송」 — 승인 API는 운영자가 모두싸인에서 이미 보낸 사실을 적는 기록 API다. 그래서 체크 3종과
 * 발송 일시·서명 기한을 필수로 받고, 우리 시스템은 기한을 계산하지 않는다.
 * 반려는 사유 2단(제목 + 상세)이 둘 다 필수이고 브랜드 화면에 그대로 나가며 되돌릴 수 없다.
 */
@DisplayName("[통합] 어드민 계약 검토 (B1·B2·C1·C2)")
class AdminContractReviewIntegrationTest extends AdminContractTestSupport {

    // ------------------------------------------------------------------ B1

    @Test
    @DisplayName("B1: 검토 대기 상세 — 양측 딥링크 · 스레드 · 8일 · 대기 경과 1일 2시간 · 승인/반려만 열림")
    void reviewPendingDetail() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);

        detail(c).andExpect(status().isOk())
                .andExpect(jsonPath("$.contract.contractNumber").value(c.getContractNumber()))
                .andExpect(jsonPath("$.contract.title").value("겨울 리페어 크림 공구"))
                .andExpect(jsonPath("$.contract.status").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.contract.statusLabel").value("검토 대기"))
                .andExpect(jsonPath("$.contract.statusTone").value("INFO"))
                .andExpect(jsonPath("$.contract.days").value(8))
                .andExpect(jsonPath("$.contract.brand.name").value("글로우랩"))
                .andExpect(jsonPath("$.contract.brand.link").value("/admin/brands/" + brand.marketId()))
                .andExpect(jsonPath("$.contract.creator.name").value("뷰티_하윤"))
                .andExpect(jsonPath("$.contract.creator.link").value("/admin/creators/" + creator.getId()))
                .andExpect(jsonPath("$.contract.threadId").value(thread.getId()))
                .andExpect(jsonPath("$.review.waitingElapsed").value("1일 2시간"))
                .andExpect(jsonPath("$.review.approvedAt").doesNotExist())
                .andExpect(jsonPath("$.stepper.signedCount").value(0))
                .andExpect(jsonPath("$.stepper.signatureRequestedAt").doesNotExist())
                .andExpect(jsonPath("$.permissions.canApprove").value(true))
                .andExpect(jsonPath("$.permissions.canReject").value(true))
                .andExpect(jsonPath("$.permissions.canUpdateSignature").value(false))
                .andExpect(jsonPath("$.permissions.canConclude").value(false))
                .andExpect(jsonPath("$.permissions.canExpire").value(false))
                .andExpect(jsonPath("$.permissions.canHandleResend").value(false))
                .andExpect(jsonPath("$.permissions.canUploadDocument").value(false))
                // 어드민은 경고 W1~W6을 판정하지 않는다 — 저장돼 있어도 응답에 싣지 않는다(설계서 0-7).
                .andExpect(jsonPath("$.warningFlags").doesNotExist())
                .andExpect(jsonPath("$.version").isNumber());
    }

    @Test
    @DisplayName("B1-b: 계약 조건·상품 항목·콘텐츠 의무를 파트너 카드와 같은 값으로 읽기 전용으로 내린다")
    void reviewPendingTermsAreReadOnlyCopies() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);

        detail(c)
                // 리페어 크림 60ml · 정가 48,000 · 공구가 33,600 · 리워드율 45% · 예상 리워드 15,120 · 최소 200개
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("리페어 크림 60ml"))
                .andExpect(jsonPath("$.items[0].regularPrice").value(48_000))
                .andExpect(jsonPath("$.items[0].groupBuyPrice").value(33_600))
                .andExpect(jsonPath("$.items[0].rewardRate").value(45.0))
                .andExpect(jsonPath("$.items[0].unitReward").value(15_120))
                .andExpect(jsonPath("$.items[0].minQuantity").value(200))
                // 고정 지급비 1,500,000원 · 공구 종료 후
                .andExpect(jsonPath("$.fixedFee.amount").value(1_500_000))
                .andExpect(jsonPath("$.fixedFee.triggerLabel").value("공구 종료 후"))
                // 피드 1 · 릴스 2 · 2차 활용 허용 · 무기한 · 사전 검수 없음
                .andExpect(jsonPath("$.content.feedCount").value(1))
                .andExpect(jsonPath("$.content.reelsCount").value(2))
                .andExpect(jsonPath("$.content.secondaryUseAllowed").value(true))
                .andExpect(jsonPath("$.content.secondaryUsePeriodType").value("UNLIMITED"))
                .andExpect(jsonPath("$.content.brandPreReview").value(false))
                .andExpect(jsonPath("$.content.note").value("겨울 시즌 리페어 라인 신규 런칭 건."));

        // 파트너 상세와 문자 단위로 같은 카드다.
        String admin = body(detail(c));
        String seller = body(sellerDetail(c));
        assertThat(readString(admin, "$.items[0].productName")).isEqualTo(readString(seller, "$.items[0].productName"));
        assertThat(readLong(admin, "$.items[0].unitReward")).isEqualTo(readLong(seller, "$.items[0].unitReward"));
        assertThat(readString(admin, "$.fixedFee.triggerLabel")).isEqualTo(readString(seller, "$.fixedFee.triggerLabel"));
    }

    @Test
    @DisplayName("운영자는 계약 조건을 고칠 수 없다 — 조건 수정 엔드포인트가 없고, 승인 요청에 조건을 실어 보내도 무시된다")
    void operatorCannotEditTerms() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        String terms = "{\"title\":\"바뀐 공구명\",\"fixedFeeAmount\":1}";

        mockMvc.perform(put(BASE + "/" + c.getId()).header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(terms)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch(BASE + "/" + c.getId()).header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(terms)).andExpect(status().isMethodNotAllowed());

        String approveWithTerms = """
                {"recipientsRegistered":true,"documentUploaded":true,"requestSent":true,
                 "signatureRequestedAt":"%s","signatureDeadlineAt":"%s",
                 "title":"바뀐 공구명","fixedFeeAmount":1}""".formatted(now.minusHours(1), now.plusDays(7));
        mockMvc.perform(post(BASE + "/" + c.getId() + "/review/approve").header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(approveWithTerms)).andExpect(status().isOk());

        Contract saved = reload(c);
        assertThat(saved.getTitle()).isEqualTo("겨울 리페어 크림 공구");
        assertThat(saved.getFixedFeeAmount()).isEqualTo(1_500_000);
    }

    @Test
    @DisplayName("B1 계약서 다운로드: 파일명은 계약서_{공구명}_{계약번호}.pdf · 제출본 기준 시각을 함께 내리고 처리자를 남긴다")
    void downloadsGeneratedContractForSubmission() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        detail(c).andExpect(jsonPath("$.documents[?(@.type=='GENERATED_DRAFT')].exists").value(contains(false)));

        String response = body(draft(c).andExpect(status().isOk()));
        assertThat(readString(response, "$.fileName"))
                .isEqualTo("계약서_겨울 리페어 크림 공구_" + c.getContractNumber() + ".pdf");
        assertThat(time(response, "$.sourceReviewRequestedAt")).isEqualTo(c.getReviewRequestedAt());
        assertThat(readLong(response, "$.expiresInSeconds")).isEqualTo(300);

        detail(c).andExpect(jsonPath("$.documents[?(@.type=='GENERATED_DRAFT')].exists").value(contains(true)));
        ContractHistory generated = historyOf(c).getLast();
        assertThat(generated.getEventType()).isEqualTo(ContractEventType.CONTRACT_PDF_GENERATED);
        assertThat(generated.getActorType()).isEqualTo(ContractActorType.ADMIN);
        assertThat(generated.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
    }

    @Test
    @DisplayName("발송 이후에는 새 계약서를 만들지 않는다 — 이미 보낸 파일과 보관본이 달라지는 경로를 막는다")
    void doesNotGenerateContractAfterSending() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        draft(c).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
        verify(renderer, never()).render(anyString(), anyString());
    }

    @Test
    @DisplayName("체결 전에는 브랜드·인플루언서도 운영자가 받는 생성본과 같은 파일을 받는다")
    void generatedContractIsSharedWithBothParties() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        String fileName = readString(body(draft(c).andExpect(status().isOk())), "$.fileName");
        approve(c, checked(now.minusMinutes(30), now.plusDays(7))).andExpect(status().isOk());

        mockMvc.perform(get(SELLER_CONTRACTS + "/" + c.getId() + "/documents/GENERATED_DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value(fileName));
        mockMvc.perform(get(CREATOR_CONTRACTS + "/" + c.getId() + "/documents/GENERATED_DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value(fileName));
        // 발송 뒤에도 운영자는 발송한 그 파일(캐시)을 다시 받을 수 있다.
        draft(c).andExpect(status().isOk());
        verify(renderer, times(1)).render(anyString(), anyString());
    }

    // ------------------------------------------------------------------ C1

    @Test
    @DisplayName("C1 승인: 서명 진행중으로 넘어가고 발송 일시·서명 기한을 입력값 그대로 기록한다")
    void approvalRecordsSendingFacts() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        LocalDateTime sentAt = spec("2026-08-13T17:30");
        LocalDateTime deadlineAt = spec("2026-08-20T23:55");

        approve(c, checked(sentAt, deadlineAt)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(c.getId()))
                .andExpect(jsonPath("$.status").value("SIGNING"))
                .andExpect(jsonPath("$.version").isNumber());

        Contract saved = reload(c);
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.SIGNING);
        assertThat(saved.getSignatureRequestedAt()).isEqualTo(sentAt);
        assertThat(saved.getSignatureDeadlineAt()).isEqualTo(deadlineAt);
        assertThat(saved.getReviewApprovedAt()).isAfterOrEqualTo(now);
        // 체크 3종은 확인용이지 기록이 아니다 — 서명 값·기준 시각은 아직 비어 있다.
        assertThat(saved.getBrandSignedAt()).isNull();
        assertThat(saved.getCreatorSignedAt()).isNull();

        String detail = body(detail(c));
        assertThat(time(detail, "$.signature.requestedAt")).isEqualTo(sentAt);
        assertThat(time(detail, "$.signature.deadlineAt")).isEqualTo(deadlineAt);
        assertThat(time(detail, "$.stepper.signatureRequestedAt")).isEqualTo(sentAt);
        assertThat(readString(detail, "$.stepper.reviewApprovedActorName")).isEqualTo(OPERATOR_NAME);
        detail(c).andExpect(jsonPath("$.permissions.canApprove").value(false))
                .andExpect(jsonPath("$.permissions.canReject").value(false))
                .andExpect(jsonPath("$.permissions.canUpdateSignature").value(true))
                .andExpect(jsonPath("$.permissions.canHandleResend").value(true))
                .andExpect(jsonPath("$.permissions.canExpire").value(false));
    }

    @Test
    @DisplayName("C1 승인: 이력은 「검토 통과」와 「양측에 전자서명 요청 발송」 두 줄 — 시각이 다르고 둘 다 처리자 실명이다")
    void approvalWritesTwoHistoryLines() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        LocalDateTime sentAt = now.minusMinutes(40);

        approve(c, checked(sentAt, now.plusDays(7))).andExpect(status().isOk());

        assertThat(historyOf(c)).extracting(ContractHistory::getEventType)
                .containsExactly(ContractEventType.SIGNATURE_SENT, ContractEventType.REVIEW_APPROVED);
        ContractHistory sent = historyOf(c).getFirst();
        ContractHistory approved = historyOf(c).getLast();
        assertThat(sent.getOccurredAt()).isEqualTo(sentAt);
        assertThat(approved.getOccurredAt()).isAfter(sentAt);
        assertThat(historyOf(c)).allSatisfy(h -> {
            assertThat(h.getActorType()).isEqualTo(ContractActorType.ADMIN);
            assertThat(h.getActorId()).isEqualTo(admin.getId());
            assertThat(h.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
        });
        detail(c).andExpect(jsonPath("$.history[*].actorDisplayName", everyItem(is(OPERATOR_NAME))));
    }

    @Test
    @DisplayName("C1 승인: 양측 화면이 서명 진행중으로 바뀌고 브랜드 편집 잠금은 유지된다 · 양측에 통지한다")
    void approvalReachesBothParties() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        // 검토 대기는 인플루언서에게 도착하지 않은 계약이다.
        creatorDetail(c).andExpect(status().isNotFound());

        LocalDateTime deadlineAt = now.plusDays(7);
        approve(c, checked(now.minusMinutes(30), deadlineAt)).andExpect(status().isOk());

        String seller = body(sellerDetail(c).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNING"))
                .andExpect(jsonPath("$.permissions.canEdit").value(false)));
        assertThat(time(seller, "$.signature.deadlineAt")).isEqualTo(deadlineAt);
        String studio = body(creatorDetail(c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNING")));
        assertThat(time(studio, "$.signature.deadlineAt")).isEqualTo(deadlineAt);

        verify(notifier).notifyBothParties(argThat(x -> x.getId().equals(c.getId())), eq("REVIEW_APPROVED"));
    }

    @Test
    @DisplayName("C1 체크 3종(수신자·업로드·발송)이 하나라도 빠지면 400 — 버튼 비활성의 서버 측 방어선")
    void approvalRequiresAllThreeChecks() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        LocalDateTime sent = now.minusHours(1), deadline = now.plusDays(7);

        for (ApproveRequest missing : new ApproveRequest[]{
                new ApproveRequest(false, true, true, sent, deadline),
                new ApproveRequest(true, null, true, sent, deadline),
                new ApproveRequest(true, true, false, sent, deadline)}) {
            approve(c, missing).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONTRACT_CHECKLIST_REQUIRED"));
        }
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.REVIEW_PENDING);
        assertThat(historyOf(c)).isEmpty();
        verify(notifier, never()).notifyBothParties(any(), anyString());
    }

    @Test
    @DisplayName("C1 시각 검증: 둘 다 필수 · 발송은 검토 요청 이후·현재 이전 · 기한은 발송보다 뒤 · 기한 상한은 없다")
    void approvalValidatesSendingAndDeadlineTimes() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        LocalDateTime requested = c.getReviewRequestedAt();

        for (ApproveRequest invalid : new ApproveRequest[]{
                checked(null, now.plusDays(7)),
                checked(now.minusHours(1), null),
                checked(requested.minusMinutes(1), now.plusDays(7)),   // 제출보다 먼저 발송
                checked(now.plusMinutes(10), now.plusDays(7)),          // 아직 보내지 않았다
                checked(now.minusHours(1), now.minusHours(1)),          // 기한 = 발송
                checked(now.minusHours(1), now.minusHours(2))}) {       // 기한 < 발송
            approve(c, invalid).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONTRACT_SIGNATURE_TIME_INVALID"));
        }
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.REVIEW_PENDING);

        // 경계: 제출과 같은 시각의 발송 · 1년 뒤 기한(모두싸인에서 정한 값이라 임계값을 발명하지 않는다)
        approve(c, checked(requested, now.plusYears(1))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("C1 승인에는 되돌림이 없다 — 검토 대기가 아닌 계약의 승인·반려는 409, 작성중은 존재하지 않는다(404)")
    void reviewActionsOnlyOnPendingReview() throws Exception {
        for (ContractStatus status : new ContractStatus[]{ContractStatus.REVIEW_REJECTED, ContractStatus.SIGNING,
                ContractStatus.CONCLUSION_PENDING, ContractStatus.CONCLUDED, ContractStatus.EXPIRED, ContractStatus.CANCELED}) {
            Contract c = seed(status);
            approve(c, checked(now.minusHours(1), now.plusDays(7))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTRACT_REVIEW_NOT_PENDING"));
            reject(c, ContractReviewRejectReason.ETC, "사유").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTRACT_REVIEW_NOT_PENDING"));
        }
        Contract draft = seed(ContractStatus.DRAFT);
        approve(draft, checked(now.minusHours(1), now.plusDays(7))).andExpect(status().isNotFound());
        reject(draft, ContractReviewRejectReason.ETC, "사유").andExpect(status().isNotFound());
        detail(draft).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ C2 · B2

    @ParameterizedTest(name = "{0}")
    @EnumSource(ContractReviewRejectReason.class)
    @DisplayName("C2 반려 사유 6종(합의 불일치·정보 불일치·이행 판정 불가·오기·누락·계정 상태·기타) 모두 상세 설명이 필수다")
    void everyRejectReasonRequiresDetail(ContractReviewRejectReason reason) throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);

        reject(c, reason, null).andExpect(status().isBadRequest());
        reject(c, reason, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_REJECT_DETAIL_REQUIRED"));
        reject(c, reason, "무엇을 어떻게 고칠지").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REJECTED"));
        assertThat(reload(c).getRejectReasonCode()).isEqualTo(reason.name());
    }

    @Test
    @DisplayName("C2 입력 검증: 사유 미선택·알 수 없는 사유·1000자 초과 설명은 400")
    void rejectValidatesInput() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);

        reject(c, null, "설명").andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/" + c.getId() + "/review/reject").header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"THRESHOLD_EXCEEDED\",\"reasonDetail\":\"설명\"}"))
                .andExpect(status().isBadRequest());
        reject(c, ContractReviewRejectReason.TYPO_OR_OMISSION, "가".repeat(1001)).andExpect(status().isBadRequest());
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.REVIEW_PENDING);
        assertThat(historyOf(c)).isEmpty();
    }

    @Test
    @DisplayName("B2 반려 완료: 사유 2단이 브랜드 화면에 그대로 나가고 브랜드 편집이 다시 열린다 · 운영자 액션은 사라진다")
    void rejectionIsShownToBrandVerbatim() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        String detailText = "2차 활용 기간이 무기한으로 되어 있습니다. 스레드에는 12개월로 협의된 기록이 있어 그대로 서명 요청을 "
                + "보낼 수 없습니다 — 기간을 명시하거나 합의 내용을 확인한 뒤 다시 요청해 주세요.";

        reject(c, ContractReviewRejectReason.AGREEMENT_MISMATCH, detailText).andExpect(status().isOk());

        detail(c).andExpect(jsonPath("$.contract.status").value("REVIEW_REJECTED"))
                .andExpect(jsonPath("$.contract.statusLabel").value("검토 반려"))
                .andExpect(jsonPath("$.contract.statusTone").value("WARNING"))
                .andExpect(jsonPath("$.review.rejectedAt").exists())
                .andExpect(jsonPath("$.review.rejectReason.code").value("AGREEMENT_MISMATCH"))
                .andExpect(jsonPath("$.review.rejectReason.detail").value(detailText))
                .andExpect(jsonPath("$.permissions.canApprove").value(false))
                .andExpect(jsonPath("$.permissions.canReject").value(false))
                .andExpect(jsonPath("$.permissions.canUpdateSignature").value(false))
                .andExpect(jsonPath("$.permissions.canConclude").value(false))
                .andExpect(jsonPath("$.permissions.canExpire").value(false))
                .andExpect(jsonPath("$.permissions.canHandleResend").value(false))
                .andExpect(jsonPath("$.permissions.canUploadDocument").value(false));

        sellerDetail(c).andExpect(jsonPath("$.status").value("REVIEW_REJECTED"))
                .andExpect(jsonPath("$.review.rejectReason.code").value("AGREEMENT_MISMATCH"))
                .andExpect(jsonPath("$.review.rejectReason.detail").value(detailText))
                .andExpect(jsonPath("$.permissions.canEdit").value(true));
        // 반려는 운영자–브랜드 사이의 일이다. 인플루언서에게는 도착하지 않는다.
        creatorDetail(c).andExpect(status().isNotFound());

        ContractHistory rejected = historyOf(c).getLast();
        assertThat(rejected.getEventType()).isEqualTo(ContractEventType.REVIEW_REJECTED);
        assertThat(rejected.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
        assertThat(rejected.getDetail()).contains(detailText);

        verify(notifier).notifySeller(argThat(x -> x.getId().equals(c.getId())), eq("REVIEW_REJECTED"));
        verify(notifier, never()).notifyCounterparty(any(), eq("REVIEW_REJECTED"));
    }

    @Test
    @DisplayName("B2 반려는 되돌릴 수 없다 — 반려된 계약에 다시 승인·반려를 눌러도 409이고 상태·사유가 그대로다")
    void rejectionCannotBeReversed() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        reject(c, ContractReviewRejectReason.INFO_MISMATCH, "상품 정보를 확인해 주세요.").andExpect(status().isOk());

        approve(c, checked(now.minusMinutes(5), now.plusDays(7))).andExpect(status().isConflict());
        reject(c, ContractReviewRejectReason.ETC, "다른 사유").andExpect(status().isConflict());

        Contract saved = reload(c);
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.REVIEW_REJECTED);
        assertThat(saved.getRejectReasonCode()).isEqualTo("INFO_MISMATCH");
        assertThat(saved.getRejectReasonDetail()).isEqualTo("상품 정보를 확인해 주세요.");
    }

    @Test
    @DisplayName("이력 주체: 어드민은 실명 「김운영」, 파트너센터·스튜디오는 운영자 실명을 받지 않는다(익명 「어드민」·「운영자」)")
    void operatorNameIsVisibleOnlyToAdmin() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        approve(c, checked(now.minusMinutes(30), now.plusDays(7))).andExpect(status().isOk());

        detail(c).andExpect(jsonPath("$.history[*].actorDisplayName", everyItem(is(OPERATOR_NAME))));
        sellerDetail(c).andExpect(status().isOk())
                .andExpect(jsonPath("$.history[?(@.actorType=='ADMIN')]").isNotEmpty())
                .andExpect(jsonPath("$.history[*].actorDisplayName", not(hasItem(OPERATOR_NAME))));
        creatorDetail(c).andExpect(status().isOk())
                .andExpect(jsonPath("$.history[?(@.actorType=='ADMIN')]").isNotEmpty())
                .andExpect(jsonPath("$.history[*].actorDisplayName", not(hasItem(OPERATOR_NAME))));
    }
}
