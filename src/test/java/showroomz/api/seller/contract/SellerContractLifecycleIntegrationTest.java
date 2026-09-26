package showroomz.api.seller.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.entity.ContractResendRequest;
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
                // 반려 계약은 아직 상대에게 나가지 않았으므로 지울 수 있다.
                .andExpect(jsonPath("$.permissions.canDelete").value(true))
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
    @DisplayName("삭제는 작성중·검토 반려만이다 — 검토 대기 이후의 되돌림은 취소이지 삭제가 아니다")
    void deletesDraftsAndRejectedOnly() throws Exception {
        long draft = createDraft();
        deleteContract(draft).andExpect(status().isNoContent());
        detail(draft).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTRACT_NOT_FOUND"));
        // 행은 남기고 표시만 한다 — 이력도 지우지 않고 삭제 한 줄이 더해진다.
        assertThat(contractRepository.findById(draft).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(historyOf(draft)).extracting(ContractHistory::getEventType)
                .containsExactly(ContractEventType.CREATED, ContractEventType.DELETED);

        // 검토 요청을 냈다가 취소하고 돌아온 초안도 지울 수 있다.
        long returned = draftReadyForReview();
        reviewRequest(returned).andExpect(status().isOk());
        cancelReviewRequest(returned).andExpect(status().isOk());
        deleteContract(returned).andExpect(status().isNoContent());
        detail(returned).andExpect(status().isNotFound());

        // 검토 반려 — 계약번호·반려 사유가 그대로 남은 채 표시만 된다.
        Contract rejected = seedInStatus(ContractStatus.REVIEW_REJECTED);
        deleteContract(rejected.getId()).andExpect(status().isNoContent());
        detail(rejected.getId()).andExpect(status().isNotFound());
        Contract kept = contractRepository.findById(rejected.getId()).orElseThrow();
        assertThat(kept.getDeletedAt()).isNotNull();
        assertThat(kept.getStatus()).isEqualTo(ContractStatus.REVIEW_REJECTED);
        assertThat(kept.getContractNumber()).isEqualTo(rejected.getContractNumber());
        assertThat(kept.getRejectReasonCode()).isEqualTo(rejected.getRejectReasonCode());

        for (ContractStatus locked : List.of(ContractStatus.REVIEW_PENDING,
                ContractStatus.SIGNING, ContractStatus.CONCLUDED, ContractStatus.CANCELED)) {
            deleteContract(seedInStatus(locked).getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTRACT_EDIT_LOCKED"));
        }
    }

    @Test
    @DisplayName("삭제된 계약은 없는 계약이다 — 목록·카운트에서 빠지고 저장·검토 요청·재삭제가 전부 404다")
    void deletedContractIsGone() throws Exception {
        long rejected = seedInStatus(ContractStatus.REVIEW_REJECTED).getId();
        String before = detailOk(rejected);
        deleteContract(rejected).andExpect(status().isNoContent());

        mockMvc.perform(get(CONTRACTS).header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get(CONTRACTS + "/summary").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(jsonPath("$.tabCounts.ALL").value(0))
                // 반려는 GNB 배지 대상이지만 지운 계약은 조치할 대상이 아니다.
                .andExpect(jsonPath("$.actionRequiredCount").value(0));

        save(rejected, validForm(versionOf(before))).andExpect(status().isNotFound());
        reviewRequest(rejected).andExpect(status().isNotFound());
        deleteContract(rejected).andExpect(status().isNotFound());
        assertThat(contractRepository.findById(rejected).orElseThrow().getStatus())
                .isEqualTo(ContractStatus.REVIEW_REJECTED);
    }

    // ── 취소 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("브랜드에게는 계약 취소가 없다 — 버튼도 엔드포인트도 없고, 되돌림은 [요청 취소]뿐이다")
    void brandHasNoContractCancel() throws Exception {
        for (ContractStatus status : ContractStatus.values()) {
            if (status == ContractStatus.DECLINED) {
                continue; // 적재가 조건부 UPDATE를 타서 목록 밖에서 따로 본다
            }
            long contractId = seedInStatus(status).getId();
            detail(contractId).andExpect(jsonPath("$.permissions.canCancel").doesNotExist());

            mockMvc.perform(post(CONTRACTS + "/" + contractId + "/cancel")
                            .header(HttpHeaders.AUTHORIZATION, brandToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reasonCode\":\"SCHEDULE_CHANGE\"}"))
                    .andExpect(status().is4xxClientError());

            // 막힌 호출은 흔적을 남기지 않는다.
            assertThat(contractRepository.findById(contractId).orElseThrow().getStatus()).isEqualTo(status);
        }
        long signing = seedInStatus(ContractStatus.SIGNING).getId();
        assertThat(historyOf(signing)).extracting(ContractHistory::getEventType)
                .doesNotContain(ContractEventType.CANCELED);
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

    // ── 시각 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("마지막 저장 시각은 브랜드의 임시저장만 찍는다 — 초안 생성·검토 요청·어드민 반려는 바꾸지 않는다")
    void updatedAtTracksBrandSavesOnly() throws Exception {
        long contractId = createDraft();
        detail(contractId).andExpect(jsonPath("$.updatedAt").doesNotExist());

        String saved = saveOk(contractId, validForm(currentVersion(contractId)));
        String savedAt = readString(saved, "$.updatedAt");
        assertThat(savedAt).isNotNull();
        assertThat(readString(detailOk(contractId), "$.updatedAt")).isEqualTo(savedAt);

        reviewRequestOk(contractId);
        assertThat(readString(detailOk(contractId), "$.updatedAt")).isEqualTo(savedAt);

        // 적재한 반려 계약 — 어드민 반려가 행을 바꿨어도 브랜드는 저장한 적이 없다.
        detail(seedInStatus(ContractStatus.REVIEW_REJECTED).getId())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("체결 시각은 최상위 concludedAt에 있다 — closure는 종결 3종 전용이라 체결완료에서 비어 있다")
    void concludedAtIsExposedOutsideClosure() throws Exception {
        Contract concluded = seedInStatus(ContractStatus.CONCLUDED);
        String body = detailOk(concluded.getId());
        assertThat(readString(body, "$.concludedAt")).isNotNull();
        detail(concluded.getId()).andExpect(jsonPath("$.closure.closedAt").doesNotExist());

        for (ContractStatus notConcluded : List.of(ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING,
                ContractStatus.CANCELED)) {
            detail(seedInStatus(notConcluded).getId()).andExpect(jsonPath("$.concludedAt").doesNotExist());
        }
    }

    // ── 스튜디오 스레드 [계약 확인] 게이트 ──────────────────────────────────

    @Test
    @DisplayName("스레드 게이트는 인플루언서에게 도착한 계약만 센다 — 작성중·검토·반려·삭제는 없는 계약이다")
    void threadGateCountsReceivedContractsOnly() {
        List<Long> marketIds = List.of(brand.marketId());
        for (ContractStatus notSent : List.of(ContractStatus.DRAFT, ContractStatus.REVIEW_PENDING,
                ContractStatus.REVIEW_REJECTED)) {
            seedInStatus(notSent);
        }
        assertThat(contractRepository.findMarketIdsWithReceivedContract(
                counterparty.getId(), marketIds, ContractStatus.RECEIVED_BY_CREATOR)).isEmpty();

        // connection_id가 비어 있는 계약(목록에서 상대를 고른 계약)도 (브랜드, 인플루언서) 쌍으로 잡힌다.
        seedInStatus(ContractStatus.SIGNING);
        assertThat(contractRepository.findMarketIdsWithReceivedContract(
                counterparty.getId(), marketIds, ContractStatus.RECEIVED_BY_CREATOR))
                .containsExactly(brand.marketId());

        Creator stranger = createConnectedCreator("다른_쇼룸", "stranger");
        assertThat(contractRepository.findMarketIdsWithReceivedContract(
                stranger.getId(), marketIds, ContractStatus.RECEIVED_BY_CREATOR)).isEmpty();
    }

    // ── 권한 · 격리 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("버튼 판정은 상태를 따른다 — 화면이 아니라 서버가 내린다")
    void permissionsFollowStatus() throws Exception {
        assertPermissions(ContractStatus.DRAFT, "canEdit", "canDelete", "canRequestReview");
        // 검토 대기 — 되돌림은 [요청 취소](작성중으로)뿐이다. 브랜드에게 계약 취소(종결)는 없다.
        assertPermissions(ContractStatus.REVIEW_PENDING, "canCancelRequest");
        // 반려 — 수정 후 재요청, 또는 삭제. 아직 상대에게 나가지 않았다.
        assertPermissions(ContractStatus.REVIEW_REJECTED, "canEdit", "canDelete", "canRequestReview");
        // 서명 요청 발송 이후 — 취소는 운영자만 한다.
        assertPermissions(ContractStatus.SIGNING, "canRequestResend");
        // 양측 서명 완료 — 브랜드가 할 조작이 없다(B4b).
        assertPermissions(ContractStatus.CONCLUSION_PENDING);
        // 공구는 체결 트랜잭션이 만든다 — 브랜드에게 생성 버튼이 없다(공구 설계서 0-2).
        assertPermissions(ContractStatus.CONCLUDED, "canRecordPayment", "canDuplicate");
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
                        .header(HttpHeaders.AUTHORIZATION, otherToken)));
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

    /** 나열한 권한만 true이고 나머지는 false여야 한다 — 「무엇이 꺼져 있는가」가 판정의 절반이다. */
    private void assertPermissions(ContractStatus status, String... allowed) throws Exception {
        long contractId = seedInStatus(status).getId();
        ResultActions result = detail(contractId).andExpect(status().isOk());

        List<String> allPermissions = List.of("canEdit", "canDelete", "canRequestReview", "canCancelRequest",
                "canRequestResend", "canRecordPayment", "canDuplicate");
        List<String> expectedTrue = List.of(allowed);
        for (String permission : allPermissions) {
            result.andExpect(jsonPath("$.permissions." + permission).value(expectedTrue.contains(permission)));
        }
    }
}
