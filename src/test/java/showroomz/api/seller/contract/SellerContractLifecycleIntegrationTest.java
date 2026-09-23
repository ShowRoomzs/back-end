package showroomz.api.seller.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 설계서 3·4-2·4-3 — 상태 전이와 그 이후.
 *
 * <p>여기서 지키려는 것은 「상태가 권한이다」라는 한 문장이다. 쓰기 API는 전부 진입 시 상태를 먼저
 * 판정하고 허용 집합 밖이면 409로 떨군다 — 화면에서 버튼이 사라지는 것은 친절이고, 집행은 서버가 한다.
 *
 * <p>되돌림 2경로(요청 취소 · 계약 취소)가 갈라져 있는지, 종결이 정말 종결인지,
 * 재작성이 <b>복사가 아니라 새 행</b>인지를 함께 본다.
 */
@DisplayName("[통합] 파트너센터 계약 전이·이후")
class SellerContractLifecycleIntegrationTest extends SellerContractTestSupport {

    // ── 검토 재요청 · 계약번호 ──────────────────────────────────────────────

    @Test
    @DisplayName("검토 반려는 편집이 다시 열리고, 재요청해도 계약번호는 그대로다")
    void resubmitsAfterRejectionKeepingContractNumber() throws Exception {
        Contract rejected = seedInStatus(ContractStatus.REVIEW_REJECTED);
        long contractId = rejected.getId();

        String before = detailOk(contractId);
        assertThat(readString(before, "$.review.rejectReason.code")).isEqualTo("INFO_MISMATCH");
        detail(contractId)
                .andExpect(jsonPath("$.permissions.canEdit").value(true))
                // 반려됐다고 지울 수 있는 것은 아니다 — 삭제는 작성중 초안만이다.
                .andExpect(jsonPath("$.permissions.canDelete").value(false))
                .andExpect(jsonPath("$.permissions.canRequestReview").value(true));

        saveOk(contractId, validForm(versionOf(before)).title("가을 앰플 신제품 공구 (수정)"));

        String resubmitted = reviewRequestOk(contractId);
        assertThat(readString(resubmitted, "$.contractNumber")).isEqualTo(rejected.getContractNumber());
        detail(contractId)
                .andExpect(jsonPath("$.status").value("REVIEW_PENDING"))
                .andExpect(jsonPath("$.review.requestedAt").exists())
                // 반려 흔적은 재요청과 함께 지운다 — 남겨두면 화면이 반려와 대기를 동시에 그린다.
                .andExpect(jsonPath("$.review.rejectedAt").doesNotExist())
                .andExpect(jsonPath("$.review.rejectReason").doesNotExist())
                .andExpect(jsonPath("$.permissions.canEdit").value(false));

        assertThat(historyOf(contractId)).filteredOn(entry ->
                entry.getEventType() == ContractEventType.REVIEW_REQUESTED).hasSize(1);
    }

    @Test
    @DisplayName("계약번호는 검토 요청 때 하루 단위로 이어 붙는다 — 버려진 초안은 번호를 먹지 않는다")
    void assignsSequentialNumbersOnlyAtReviewRequest() throws Exception {
        createDraft();
        long first = draftReadyForReview();
        long second = draftReadyForReview();

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        reviewRequest(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.contractNumber").value("CTR-" + today + "-001"));
        reviewRequest(second).andExpect(status().isOk())
                .andExpect(jsonPath("$.contractNumber").value("CTR-" + today + "-002"));

        // 요청 취소 후 다시 요청해도 번호를 새로 타지 않는다 — 이미 어드민 큐에 나간 번호다.
        cancelReviewRequest(first).andExpect(status().isOk());
        reviewRequest(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.contractNumber").value("CTR-" + today + "-001"))
                .andExpect(jsonPath("$.contractNumber").value(matchesPattern("CTR-\\d{8}-\\d{3}")));
    }

    // ── 삭제 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("삭제는 작성중 초안만이다 — 검토 요청 이후의 되돌림은 취소이지 삭제가 아니다")
    void deletesDraftsOnly() throws Exception {
        long draft = createDraft();
        deleteContract(draft).andExpect(status().isNoContent());
        detail(draft).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
        // 생성 이력 한 줄은 어떤 초안에도 반드시 있다 — 함께 치우지 않으면 FK 때문에 삭제 자체가 깨진다.
        assertThat(historyOf(draft)).isEmpty();

        // 검토 요청을 냈다가 취소하고 돌아온 초안도 지울 수 있다. 이력이 세 줄이고 항목도 달려 있다.
        long returned = draftReadyForReview();
        reviewRequest(returned).andExpect(status().isOk());
        cancelReviewRequest(returned).andExpect(status().isOk());
        deleteContract(returned).andExpect(status().isNoContent());
        assertThat(contractRepository.findById(returned)).isEmpty();
        assertThat(historyOf(returned)).isEmpty();

        for (ContractStatus locked : List.of(ContractStatus.REVIEW_PENDING, ContractStatus.REVIEW_REJECTED,
                ContractStatus.SIGNING, ContractStatus.CONCLUDED, ContractStatus.CANCELED)) {
            deleteContract(seedInStatus(locked).getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTRACT_EDIT_LOCKED"));
        }
    }

    // ── 계약 취소 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("계약 취소는 서명 구간에서만 가능하고 언제나 종결이다")
    void cancelsOnlyInSigningWindow() throws Exception {
        long signing = seedInStatus(ContractStatus.SIGNING).getId();

        cancelContract(signing, ContractCloseReasonCode.SCHEDULE_CHANGE, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.statusLabel").value("취소"))
                .andExpect(jsonPath("$.closure.closedAt").exists())
                .andExpect(jsonPath("$.closure.actorType").value("SELLER"))
                .andExpect(jsonPath("$.closure.reasonCode").value("SCHEDULE_CHANGE"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("공구 일정 변경"))
                // 종결되면 지급 의무도 함께 사라진다 — 화면이 고정 지급비 안내를 거두는 근거다.
                .andExpect(jsonPath("$.fixedFee.obligationAlive").value(false))
                .andExpect(jsonPath("$.permissions.canCancel").value(false))
                .andExpect(jsonPath("$.permissions.canDuplicate").value(true));

        assertThat(historyOf(signing)).extracting(ContractHistory::getEventType)
                .contains(ContractEventType.CANCELED);

        // 한쪽이 서명한 뒤라도 양측 서명 전이면 아직 취소할 수 있다(B4a · B4c).
        LocalDateTime signedAt = LocalDateTime.now().minusHours(1).withNano(0);
        cancelContract(seedInStatus(ContractStatus.SIGNING,
                        contract -> contract.updateSignatures(signedAt, null, signedAt)).getId(),
                ContractCloseReasonCode.OUT_OF_STOCK, null).andExpect(status().isOk());
        cancelContract(seedInStatus(ContractStatus.SIGNING,
                        contract -> contract.updateSignatures(null, signedAt, signedAt)).getId(),
                ContractCloseReasonCode.OUT_OF_STOCK, null).andExpect(status().isOk());

        for (ContractStatus notCancelable : List.of(ContractStatus.DRAFT, ContractStatus.REVIEW_REJECTED,
                ContractStatus.CONCLUDED, ContractStatus.EXPIRED)) {
            cancelContract(seedInStatus(notCancelable).getId(), ContractCloseReasonCode.SCHEDULE_CHANGE, null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
        }
    }

    @Test
    @DisplayName("양측 서명이 모두 끝나면 취소할 수 없다 — 버튼도 없고 호출도 막는다")
    void refusesCancelAfterBothPartiesSigned() throws Exception {
        long contractId = seedInStatus(ContractStatus.CONCLUSION_PENDING).getId();

        // B4b — 체결 처리 대기. 모두싸인에는 이미 양측 서명이 남아 있다.
        detail(contractId)
                .andExpect(jsonPath("$.signature.brandSignedAt").exists())
                .andExpect(jsonPath("$.signature.creatorSignedAt").exists())
                .andExpect(jsonPath("$.permissions.canCancel").value(false));

        cancelContract(contractId, ContractCloseReasonCode.CONDITION_REVIEW, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));

        // 막힌 호출은 흔적을 남기지 않는다 — 상태도 종결 필드도 이력도 그대로다.
        detail(contractId)
                .andExpect(jsonPath("$.status").value("CONCLUSION_PENDING"))
                .andExpect(jsonPath("$.closure.closedAt").doesNotExist())
                .andExpect(jsonPath("$.fixedFee.obligationAlive").value(true));
        assertThat(historyOf(contractId)).extracting(ContractHistory::getEventType)
                .doesNotContain(ContractEventType.CANCELED);
    }

    @Test
    @DisplayName("기타 사유는 메모가 필수다 — 상대에게 그대로 전달되는 문구다")
    void requiresMemoForEtcReason() throws Exception {
        long contractId = seedInStatus(ContractStatus.SIGNING).getId();

        cancelContract(contractId, ContractCloseReasonCode.ETC, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_CANCEL_REASON_MEMO_REQUIRED"));
        cancelContract(contractId, ContractCloseReasonCode.ETC, "   ")
                .andExpect(status().isBadRequest());

        cancelContract(contractId, ContractCloseReasonCode.ETC, "  담당자 퇴사로 진행이 어렵습니다.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closure.reasonLabel").value("기타"))
                .andExpect(jsonPath("$.closure.memo").value("담당자 퇴사로 진행이 어렵습니다."));
    }

    @Test
    @DisplayName("검토 대기에서 [요청 취소]는 종결이 아니다 — 사유도 받지 않고 이력만 남는다")
    void cancelReviewRequestIsNotAClosure() throws Exception {
        long contractId = seedInStatus(ContractStatus.REVIEW_PENDING).getId();

        cancelReviewRequest(contractId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.closure.closedAt").doesNotExist())
                .andExpect(jsonPath("$.closure.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.permissions.canEdit").value(true))
                .andExpect(jsonPath("$.permissions.canDelete").value(true));

        assertThat(historyOf(contractId)).extracting(ContractHistory::getEventType)
                .contains(ContractEventType.REVIEW_REQUEST_CANCELED);

        // 두 번째 호출은 이미 작성중이라 막힌다.
        cancelReviewRequest(contractId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));

        for (ContractStatus notPending : List.of(ContractStatus.SIGNING, ContractStatus.CONCLUDED)) {
            cancelReviewRequest(seedInStatus(notPending).getId()).andExpect(status().isConflict());
        }
    }

    // ── 체결 이후 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("[서명 안내 다시 받기]는 발송이 아니다 — 상태를 바꾸지 않고 연타도 쌓이지 않는다")
    void resendRequestIsIdempotentWhileUnhandled() throws Exception {
        long contractId = seedInStatus(ContractStatus.SIGNING).getId();

        String first = requestResend(contractId).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRequested").value(false))
                .andReturn().getResponse().getContentAsString();

        requestResend(contractId).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRequested").value(true))
                .andExpect(jsonPath("$.resendRequestId").value(readLong(first, "$.resendRequestId")));

        detail(contractId).andExpect(jsonPath("$.status").value("SIGNING"));
        assertThat(contractResendRequestRepository.findByContractIdOrderByRequestedAtDescIdDesc(contractId))
                .hasSize(1);
        assertThat(historyOf(contractId)).filteredOn(entry ->
                entry.getEventType() == ContractEventType.RESEND_REQUESTED).hasSize(1);

        // 어드민이 처리하고 나면 다시 요청할 수 있다 — 억제하는 것은 미처리 요청의 중복뿐이다.
        transactionTemplate.executeWithoutResult(tx -> contractResendRequestRepository.handleAll(
                contractId, 1L, LocalDateTime.now()));
        requestResend(contractId).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRequested").value(false));
        assertThat(contractResendRequestRepository.findByContractIdOrderByRequestedAtDescIdDesc(contractId))
                .hasSize(2);

        for (ContractStatus notSigning : List.of(ContractStatus.CONCLUSION_PENDING, ContractStatus.CONCLUDED,
                ContractStatus.DRAFT)) {
            requestResend(seedInStatus(notSigning).getId()).andExpect(status().isConflict());
        }
    }

    @Test
    @DisplayName("고정 지급비 지급 기록은 체결완료에서 한 번만 된다 — 되돌리는 경로는 없다")
    void recordsFixedFeePaymentOnce() throws Exception {
        long contractId = seedInStatus(ContractStatus.CONCLUDED).getId();

        detail(contractId)
                .andExpect(jsonPath("$.permissions.canRecordPayment").value(true))
                .andExpect(jsonPath("$.fixedFee.paidAt").doesNotExist());

        recordFixedFeePayment(contractId).andExpect(status().isOk())
                .andExpect(jsonPath("$.fixedFee.paidAt").exists())
                .andExpect(jsonPath("$.permissions.canRecordPayment").value(false));

        recordFixedFeePayment(contractId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_FIXED_FEE_ALREADY_PAID"));

        assertThat(historyOf(contractId)).extracting(ContractHistory::getEventType)
                .contains(ContractEventType.FIXED_FEE_PAID);

        // 0원 계약에는 지급할 것이 없다 — 버튼도 없고 호출도 막는다.
        long freeOfCharge = seedInStatus(ContractStatus.CONCLUDED,
                contract -> terms(contract, "무상 협업", baseStartAt(), baseStartAt().plusDays(9), 0)).getId();
        detail(freeOfCharge).andExpect(jsonPath("$.permissions.canRecordPayment").value(false));
        recordFixedFeePayment(freeOfCharge).andExpect(status().isConflict());

        // 체결 전에는 지급 기록도 없다.
        recordFixedFeePayment(seedInStatus(ContractStatus.SIGNING).getId()).andExpect(status().isConflict());
    }

    // ── 재작성 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("재작성은 복사가 아니라 새 행이다 — 다시 받아야 하는 값은 비워서 넘긴다")
    void duplicateCreatesNewDraftAndClearsWhatMustBeReAgreed() throws Exception {
        Contract source = seedInStatus(ContractStatus.CONCLUDED);

        String response = duplicate(source.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceContractId").value(source.getId()))
                // 비운 값들 때문에 새 계약은 바로 검토 요청할 수 없다 — 그 사실을 응답이 함께 알린다.
                .andExpect(jsonPath("$.validation.canSubmit").value(false))
                .andExpect(jsonPath("$.validation.hardViolations[*].code", hasItem("PERIOD_REQUIRED")))
                .andExpect(jsonPath("$.validation.hardViolations[*].code", hasItem("CONTENT_DUE_DATE_REQUIRED")))
                .andExpect(jsonPath("$.validation.hardViolations[*].code", hasItem("H8")))
                .andReturn().getResponse().getContentAsString();

        long copyId = readLong(response, "$.contractId");
        detail(copyId)
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sourceContractId").value(source.getId()))
                // 승계하는 것 — 상대 · 공구명 · 상품 항목 4열 · 고정 지급비 · 콘텐츠 의무 · 비고
                .andExpect(jsonPath("$.title").value("가을 앰플 신제품 공구"))
                .andExpect(jsonPath("$.counterparty.creatorId").value(counterparty.getId()))
                .andExpect(jsonPath("$.items[0].productName").value("수분진정 세럼 30ml"))
                .andExpect(jsonPath("$.items[0].groupBuyPrice").value(28_000))
                .andExpect(jsonPath("$.items[0].minQuantity").value(300))
                .andExpect(jsonPath("$.items[0].unitReward").value(4_200))
                .andExpect(jsonPath("$.fixedFee.amount").value(500_000))
                .andExpect(jsonPath("$.content.feedCount").value(1))
                .andExpect(jsonPath("$.content.note").value("비고"))
                // 비우는 것 — 기간 · 게시 완료 기한 · 고지 확인 · 계약번호 · 서명 이력
                .andExpect(jsonPath("$.period.startAt").doesNotExist())
                .andExpect(jsonPath("$.period.endAt").doesNotExist())
                .andExpect(jsonPath("$.content.dueDate").doesNotExist())
                .andExpect(jsonPath("$.fixedFee.noticeAgreedAt").doesNotExist())
                .andExpect(jsonPath("$.contractNumber").doesNotExist())
                .andExpect(jsonPath("$.signature.requestedAt").doesNotExist())
                .andExpect(jsonPath("$.permissions.canEdit").value(true));

        // 원 계약은 건드리지 않는다.
        detail(source.getId())
                .andExpect(jsonPath("$.status").value("CONCLUDED"))
                .andExpect(jsonPath("$.contractNumber").value(source.getContractNumber()));

        assertThat(historyOf(copyId)).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getEventType()).isEqualTo(ContractEventType.CREATED);
                    assertThat(entry.getDetail()).contains(source.getContractNumber());
                });
    }

    @Test
    @DisplayName("재작성은 옛 정가가 아니라 지금 상품을 다시 본다")
    void duplicateRefreshesProductSnapshots() throws Exception {
        Contract source = seedInStatus(ContractStatus.CONCLUDED);

        changeRegularPrice(serum, 20_000);
        changeDisplayStatus(serum, ProductDisplayStatus.HIDDEN);

        String response = duplicate(source.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validation.hardViolations[*].code", hasItem("H1")))
                .andExpect(jsonPath("$.validation.hardViolations[*].code", hasItem("ITEM_PRODUCT_NOT_DISPLAYED")))
                .andReturn().getResponse().getContentAsString();

        // 스냅샷이 갱신돼 있어야 화면의 정가와 판정이 같은 값을 본다.
        detail(readLong(response, "$.contractId"))
                .andExpect(jsonPath("$.items[0].regularPrice").value(20_000))
                .andExpect(jsonPath("$.items[0].groupBuyPrice").value(28_000));
    }

    @Test
    @DisplayName("진행 중 계약은 재작성할 수 없고 종결·체결에서만 열린다")
    void allowsDuplicateOnlyAfterClosure() throws Exception {
        for (ContractStatus inProgress : List.of(ContractStatus.DRAFT, ContractStatus.REVIEW_PENDING,
                ContractStatus.REVIEW_REJECTED, ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING)) {
            duplicate(seedInStatus(inProgress).getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTRACT_NOT_DUPLICABLE"));
        }

        for (ContractStatus closed : List.of(ContractStatus.CONCLUDED, ContractStatus.DECLINED,
                ContractStatus.EXPIRED, ContractStatus.CANCELED)) {
            duplicate(seedInStatus(closed).getId()).andExpect(status().isCreated());
        }
    }

    // ── 상대 고정 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("스레드에서 시작한 계약은 상대가 고정된다 — 연결 근거는 요청이 아니라 서버가 찾는다")
    void fixesCounterpartyWhenStartedFromThread() throws Exception {
        long contractId = createDraft(counterparty.getId());

        detail(contractId)
                .andExpect(jsonPath("$.counterparty.creatorId").value(counterparty.getId()))
                .andExpect(jsonPath("$.counterparty.fixed").value(true))
                .andExpect(jsonPath("$.counterparty.connectionId").value(counterpartyConnection.getId()));

        Creator another = createConnectedCreator("뷰티_소연", "soyeon");
        save(contractId, validForm(0L).creator(another.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_COUNTERPARTY_FIXED"));
        save(contractId, validForm(0L).creator(null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_COUNTERPARTY_FIXED"));

        saveOk(contractId, validForm(0L).creator(counterparty.getId()));
    }

    @Test
    @DisplayName("연결되지 않은 상대로는 계약을 시작할 수 없다")
    void rejectsDraftForUnconnectedCounterparty() throws Exception {
        Creator stranger = createCreator("모르는_쇼룸", "stranger");

        mockMvc.perform(post(CONTRACTS)
                        .header(HttpHeaders.AUTHORIZATION, brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\":%d}".formatted(stranger.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_COUNTERPARTY_NOT_CONNECTED"));
    }

    // ── 권한 · 격리 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("버튼 판정은 상태를 따른다 — 화면이 아니라 서버가 내린다")
    void permissionsFollowStatus() throws Exception {
        assertPermissions(ContractStatus.DRAFT, "canEdit", "canDelete", "canRequestReview");
        assertPermissions(ContractStatus.REVIEW_PENDING, "canCancelRequest");
        assertPermissions(ContractStatus.REVIEW_REJECTED, "canEdit", "canRequestReview");
        assertPermissions(ContractStatus.SIGNING, "canCancel", "canRequestResend");
        // 양측 서명 완료 — 브랜드가 할 조작이 없다(B4b). 취소도 닫힌다.
        assertPermissions(ContractStatus.CONCLUSION_PENDING);
        assertPermissions(ContractStatus.CONCLUDED, "canRecordPayment", "canCreateGroupBuy", "canDuplicate");
        assertPermissions(ContractStatus.CANCELED, "canDuplicate");
        assertPermissions(ContractStatus.EXPIRED, "canDuplicate");
        assertPermissions(ContractStatus.DECLINED, "canDuplicate");
    }

    @Test
    @DisplayName("남의 브랜드 계약은 조회도 조작도 403이다 — 없는 계약(404)과 구분해 내린다")
    void blocksEveryPathIntoAnotherBrandsContract() throws Exception {
        BrandFixture.Brand other = fixture.createBrand("other@showroomz.test", "아더랩");
        String otherToken = sellerToken(other.seller());
        Contract mine = seedInStatus(ContractStatus.SIGNING);

        List<ResultActions> forbidden = List.of(
                mockMvc.perform(get(CONTRACTS + "/" + mine.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherToken)),
                mockMvc.perform(post(CONTRACTS + "/" + mine.getId() + "/validate")
                        .header(HttpHeaders.AUTHORIZATION, otherToken)),
                mockMvc.perform(post(CONTRACTS + "/" + mine.getId() + "/resend-request")
                        .header(HttpHeaders.AUTHORIZATION, otherToken)),
                mockMvc.perform(post(CONTRACTS + "/" + mine.getId() + "/duplicate")
                        .header(HttpHeaders.AUTHORIZATION, otherToken)),
                mockMvc.perform(post(CONTRACTS + "/" + mine.getId() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"SCHEDULE_CHANGE\"}")));
        for (ResultActions attempt : forbidden) {
            attempt.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CONTRACT_NOT_OWNED_BY_SELLER"));
        }

        // 존재하지 않는 계약은 404다 — 남의 계약(403)과 화면이 달라야 한다.
        detail(999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ 헬퍼

    private List<ContractHistory> historyOf(long contractId) {
        return contractHistoryRepository.findByContractIdOrderByOccurredAtAscIdAsc(contractId);
    }

    /** 나열한 권한만 true이고 나머지 8종은 false여야 한다 — 「무엇이 꺼져 있는가」가 판정의 절반이다. */
    private void assertPermissions(ContractStatus status, String... allowed) throws Exception {
        long contractId = seedInStatus(status).getId();
        ResultActions result = detail(contractId).andExpect(status().isOk());

        List<String> allPermissions = List.of("canEdit", "canDelete", "canRequestReview", "canCancelRequest",
                "canCancel", "canRequestResend", "canRecordPayment", "canCreateGroupBuy", "canDuplicate");
        List<String> expectedTrue = List.of(allowed);
        for (String permission : allPermissions) {
            result.andExpect(jsonPath("$.permissions." + permission).value(expectedTrue.contains(permission)));
        }
    }
}
