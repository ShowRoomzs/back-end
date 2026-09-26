package showroomz.api.admin.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.api.seller.contract.dto.ContractReviewRequestRequest;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.type.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시안 전체 흐름 — 브랜드 제출부터 양측 체결본 다운로드까지를 <b>세 서피스의 실제 API</b>로 한 번 태운다.
 *
 * <p>생성규격 §7의 10단계: ① 브랜드 검토 요청 → ②·③ 계약서 생성·다운로드 → (반려 → 브랜드 수정 → 재요청 → 새 계약서)
 * → ④·⑤ 모두싸인 발송 후 승인 → (재발송 요청·처리) → ⑦ 서명 현황 갱신 → ⑧ 체결본 업로드 → ⑨ 체결 완료 → ⑩ 양측 다운로드.
 * 개별 규칙은 다른 테스트가 보고, 여기서는 앞 단계가 만든 값이 다음 화면에 그대로 이어지는지를 본다.
 */
@DisplayName("[통합] 어드민 계약 관리 전체 흐름")
class AdminContractLifecycleIntegrationTest extends AdminContractTestSupport {

    @Test
    @DisplayName("제출 → 반려 → 재요청 → 승인 → 재발송 → 서명 → 문서 업로드 → 체결 → 양측 다운로드")
    void fullLifecycle() throws Exception {
        // ① 브랜드가 스레드에서 계약을 작성해 검토를 요청한다.
        long id = createAndSubmit();
        Contract c = contracts.findById(id).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.REVIEW_PENDING);

        // A1 — 검토 대기 큐와 목록에 도착한다.
        summary().andExpect(jsonPath("$.queues.REVIEW").value(1)).andExpect(jsonPath("$.actionRequiredCount").value(1));
        list("queue", "REVIEW").andExpect(jsonPath("$.content[0].contractId").value(id))
                .andExpect(jsonPath("$.content[0].brandName").value("글로우랩"))
                .andExpect(jsonPath("$.content[0].creatorName").value("뷰티_하윤"));

        // ②·③ 검토 요청이 만든 제출본 기준 계약서를 운영자가 받는다.
        String firstDraft = body(draft(c).andExpect(status().isOk()));
        LocalDateTime firstSubmission = reload(c).getReviewRequestedAt();
        assertThat(time(firstDraft, "$.sourceReviewRequestedAt")).isEqualTo(firstSubmission.withNano(0));

        // C2 — 반려. 브랜드 편집이 다시 열리고 사유가 그대로 보인다.
        reject(c, ContractReviewRejectReason.AGREEMENT_MISMATCH, "2차 활용 기간을 6개월로 명시해 주세요.").andExpect(status().isOk());
        summary().andExpect(jsonPath("$.queues.REVIEW").value(0)).andExpect(jsonPath("$.tabCounts.ALL").value(1));
        String rejected = body(sellerDetail(c).andExpect(jsonPath("$.status").value("REVIEW_REJECTED"))
                .andExpect(jsonPath("$.review.rejectReason.detail").value("2차 활용 기간을 6개월로 명시해 주세요."))
                .andExpect(jsonPath("$.permissions.canEdit").value(true)));

        // 브랜드가 고쳐서 다시 요청한다 — 목록에 재등장하고 반려 사유는 지워진다.
        mockMvc.perform(put(SELLER_CONTRACTS + "/" + id).header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(form(readLong(rejected, "$.version"), 6))))
                .andExpect(status().isOk());
        requestReview(id);
        summary().andExpect(jsonPath("$.queues.REVIEW").value(1));
        String resubmitted = body(detail(c).andExpect(jsonPath("$.contract.status").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.review.rejectReason").doesNotExist())
                .andExpect(jsonPath("$.content.secondaryUseMonths").value(6))
                .andExpect(jsonPath("$.permissions.canApprove").value(true)));
        LocalDateTime secondSubmission = reload(c).getReviewRequestedAt();
        assertThat(secondSubmission).isAfter(firstSubmission);
        assertThat(time(resubmitted, "$.review.requestedAt")).isEqualTo(secondSubmission.withNano(0));
        assertThat(reload(c).getContractNumber()).isEqualTo(c.getContractNumber());

        // 조건이 바뀌었으므로 재요청이 계약서를 새로 만든다 — 운영자가 받기 전에 이미 새 제출본 파일이다.
        String secondDocument = body(downloadDocument(c, ContractDocumentType.GENERATED_DRAFT).andExpect(status().isOk()));
        assertThat(time(secondDocument, "$.sourceReviewRequestedAt")).isEqualTo(secondSubmission.withNano(0));
        String secondDraft = body(draft(c).andExpect(status().isOk()));
        assertThat(time(secondDraft, "$.sourceReviewRequestedAt")).isEqualTo(secondSubmission.withNano(0));
        // 렌더링은 제출 두 번뿐이다 — 운영자 다운로드는 제출 시 만든 파일을 그대로 준다.
        verify(renderer, times(2)).render(anyString(), anyString());

        // ④·⑤ 모두싸인에서 보낸 뒤 승인한다.
        LocalDateTime sentAt = LocalDateTime.now();
        LocalDateTime deadlineAt = sentAt.plusDays(7).withHour(23).withMinute(55).withSecond(0).withNano(0);
        approve(c, checked(sentAt, deadlineAt)).andExpect(status().isOk());
        sellerDetail(c).andExpect(jsonPath("$.status").value("SIGNING")).andExpect(jsonPath("$.permissions.canEdit").value(false));
        creatorDetail(c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNING"));

        // 인플루언서가 [서명 안내 다시 받기]를 누르고 운영자가 모두싸인에서 재발송한 뒤 기록한다.
        mockMvc.perform(post(CREATOR_CONTRACTS + "/" + id + "/resend-request").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isOk());
        summary().andExpect(jsonPath("$.queues.RESEND").value(1));
        handleResend(c).andExpect(status().isOk());
        summary().andExpect(jsonPath("$.queues.RESEND").value(0));

        // ⑦ 서명 현황 갱신 — 브랜드 먼저, 그다음 인플루언서.
        LocalDateTime brandSignedAt = LocalDateTime.now();
        signatures(c, brandSignedAt, null, versionOf(c)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNING"));
        LocalDateTime creatorSignedAt = LocalDateTime.now();
        signatures(c, brandSignedAt, creatorSignedAt, versionOf(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUSION_PENDING"));
        summary().andExpect(jsonPath("$.queues.CONCLUSION").value(1));

        // ⑧ 체결본 2종 업로드 → ⑨ 체결 완료.
        upload(c, ContractDocumentType.SIGNED_PDF, "서명완료_가을앰플신제품공구.pdf").andExpect(status().isOk());
        detail(c).andExpect(jsonPath("$.permissions.canConclude").value(false));
        upload(c, ContractDocumentType.AUDIT_TRAIL, "감사추적인증서.pdf").andExpect(status().isOk());
        detail(c).andExpect(jsonPath("$.permissions.canConclude").value(true));
        conclude(c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUDED"));

        // ⑩ 양측이 각자 체결본을 받는다.
        for (ContractDocumentType type : List.of(ContractDocumentType.SIGNED_PDF, ContractDocumentType.AUDIT_TRAIL)) {
            mockMvc.perform(get(SELLER_CONTRACTS + "/" + id + "/documents/" + type).header(HttpHeaders.AUTHORIZATION, brandToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get(CREATOR_CONTRACTS + "/" + id + "/documents/" + type).header(HttpHeaders.AUTHORIZATION, creatorToken))
                    .andExpect(status().isOk());
        }
        summary().andExpect(jsonPath("$.actionRequiredCount").value(0)).andExpect(jsonPath("$.tabCounts.CONCLUDED").value(1));

        // 모든 수동 처리에 처리자·시각이 남는다 — 운영자 이력은 전부 실명 「김운영」.
        // 계약서 생성은 검토 요청이 하므로 SYSTEM 주체다.
        assertThat(historyOf(c)).filteredOn(h -> h.getEventType() == ContractEventType.CONTRACT_PDF_GENERATED)
                .hasSize(2)
                .allSatisfy(h -> assertThat(h.getActorType()).isEqualTo(ContractActorType.SYSTEM));
        List<ContractHistory> history = historyOf(c);
        assertThat(history).extracting(ContractHistory::getEventType).containsSubsequence(
                ContractEventType.REVIEW_REQUESTED, ContractEventType.CONTRACT_PDF_GENERATED, ContractEventType.REVIEW_REJECTED,
                ContractEventType.REVIEW_REQUESTED, ContractEventType.CONTRACT_PDF_GENERATED,
                ContractEventType.REVIEW_APPROVED, ContractEventType.RESEND_REQUESTED, ContractEventType.RESEND_HANDLED,
                ContractEventType.SIGNATURE_UPDATED, ContractEventType.SIGNATURE_UPDATED,
                ContractEventType.DOCUMENT_UPLOADED, ContractEventType.DOCUMENT_UPLOADED, ContractEventType.CONCLUDED);
        assertThat(history).extracting(ContractHistory::getEventType).contains(ContractEventType.SIGNATURE_SENT);
        assertThat(history).filteredOn(h -> h.getActorType() == ContractActorType.ADMIN)
                .isNotEmpty()
                .allSatisfy(h -> {
                    assertThat(h.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
                    assertThat(h.getActorId()).isEqualTo(admin.getId());
                });
        detail(c).andExpect(jsonPath("$.history.length()").value(history.size()));
    }

    @Test
    @DisplayName("브랜드가 검토 요청을 취소하면 운영자 목록에서 사라지고 승인할 수 없다(작성중은 도착하지 않는다)")
    void withdrawnReviewRequestLeavesAdminQueue() throws Exception {
        long id = createAndSubmit();
        Contract c = contracts.findById(id).orElseThrow();
        summary().andExpect(jsonPath("$.queues.REVIEW").value(1));

        mockMvc.perform(post(SELLER_CONTRACTS + "/" + id + "/review-request/cancel").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk());

        summary().andExpect(jsonPath("$.queues.REVIEW").value(0)).andExpect(jsonPath("$.tabCounts.ALL").value(0));
        list().andExpect(jsonPath("$.content").isEmpty());
        detail(c).andExpect(status().isNotFound());
        approve(c, checked(LocalDateTime.now(), LocalDateTime.now().plusDays(7))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("브랜드가 반려 계약을 삭제하면 운영자 목록·카운트·상세에서 사라지되 번호·이력은 남는다")
    void deletedRejectedContractLeavesAdminButKeepsRecord() throws Exception {
        long id = createAndSubmit();
        Contract c = contracts.findById(id).orElseThrow();
        reject(c, ContractReviewRejectReason.AGREEMENT_MISMATCH, "2차 활용 기간을 명시해 주세요.").andExpect(status().isOk());
        summary().andExpect(jsonPath("$.tabCounts.ALL").value(1));

        mockMvc.perform(delete(SELLER_CONTRACTS + "/" + id).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isNoContent());

        summary().andExpect(jsonPath("$.tabCounts.ALL").value(0));
        list().andExpect(jsonPath("$.content").isEmpty());
        detail(c).andExpect(status().isNotFound());
        draft(c).andExpect(status().isNotFound());

        Contract deleted = reload(c);
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getStatus()).isEqualTo(ContractStatus.REVIEW_REJECTED);
        assertThat(deleted.getContractNumber()).isEqualTo(c.getContractNumber());
        assertThat(historyOf(c)).extracting(ContractHistory::getEventType).containsSubsequence(
                ContractEventType.REVIEW_REQUESTED, ContractEventType.REVIEW_REJECTED, ContractEventType.DELETED);
    }

    @Test
    @DisplayName("어드민 계약 API 14종은 전부 운영자 전용이다 — 브랜드·인플루언서 토큰은 403이고 아무것도 바뀌지 않는다")
    void everyEndpointIsOperatorOnly() throws Exception {
        Contract c = seed(ContractStatus.REVIEW_PENDING);
        String base = BASE + "/" + c.getId();
        String json = "{}";
        for (String token : List.of(brandToken, creatorToken)) {
            var requests = List.of(
                    get(BASE), get(BASE + "/summary"), get(base),
                    post(base + "/review/approve").content(json), post(base + "/review/reject").content(json),
                    put(base + "/signatures").content(json), post(base + "/conclude"), post(base + "/expire").content(json),
                    post(base + "/resend/handle"), post(base + "/documents/presign").content(json),
                    post(base + "/documents").content(json), delete(base + "/documents/SIGNED_PDF"),
                    get(base + "/documents/SIGNED_PDF"), get(base + "/document-draft"));
            for (var request : requests) {
                mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isForbidden());
            }
        }
        assertThat(reload(c).getStatus()).isEqualTo(ContractStatus.REVIEW_PENDING);
        assertThat(historyOf(c)).isEmpty();
    }

    // ------------------------------------------------------------------ 브랜드 조작

    private long createAndSubmit() throws Exception {
        String created = body(mockMvc.perform(post(SELLER_CONTRACTS).header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"creatorId\":%d}".formatted(creator.getId())))
                .andExpect(status().isCreated()));
        long id = readLong(created, "$.contractId");
        String detail = body(mockMvc.perform(get(SELLER_CONTRACTS + "/" + id).header(HttpHeaders.AUTHORIZATION, brandToken)));
        mockMvc.perform(put(SELLER_CONTRACTS + "/" + id).header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(form(readLong(detail, "$.version"), 12))))
                .andExpect(status().isOk());
        requestReview(id);
        return id;
    }

    private void requestReview(long id) throws Exception {
        mockMvc.perform(post(SELLER_CONTRACTS + "/" + id + "/review-request").header(HttpHeaders.AUTHORIZATION, brandToken)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(new ContractReviewRequestRequest(List.of()))))
                .andExpect(status().isOk());
    }

    /** 하드 검증·경고를 하나도 건드리지 않는 조건 — 시작 D+10 · 10일 · 고정 지급비 0원 · 리워드율 15%. */
    private ContractUpdateRequest form(long version, int secondaryUseMonths) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(10).withNano(0);
        return new ContractUpdateRequest(version, creator.getId(), "가을 앰플 신제품 공구", startAt, startAt.plusDays(9),
                0, FixedFeeTrigger.POST_REGISTERED, false, 1, 1, 0, startAt.plusDays(12).toLocalDate(),
                true, SecondaryUsePeriodType.FIXED, secondaryUseMonths, false, null,
                List.of(new ContractUpdateRequest.Item(null, serum.getProductId(), 28_000, new BigDecimal("15.0"), 300)));
    }
}
