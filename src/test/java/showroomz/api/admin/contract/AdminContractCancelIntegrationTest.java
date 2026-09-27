package showroomz.api.admin.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCancelRequestChannel;
import showroomz.domain.contract.type.ContractCloseReasonCode;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영자 [계약 취소] — 서명 요청 발송 이후 체결 전({@code SIGNING}·{@code CONCLUSION_PENDING}).
 *
 * <p>이 구간은 브랜드가 취소할 수 없다. 모두싸인에 양측 서명 요청이 나가 있어서 우리 상태만 종결하면
 * 상대가 여전히 서명 링크를 들고 있다 — 요청을 거둘 수 있는 운영자만 취소하고, API가 없으니
 * 「모두싸인에서 서명 요청을 회수했다」는 확인을 체크리스트로 받는다.
 */
@DisplayName("[통합] 어드민 계약 취소")
class AdminContractCancelIntegrationTest extends AdminContractTestSupport {

    private static final DateTimeFormatter RESPONSE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final String MEMO = "브랜드 요청으로 서명 요청을 회수했습니다.";

    @Test
    @DisplayName("서명 진행중(한쪽 서명 포함)·체결 처리 대기는 운영자가 취소할 수 있고 종결 주체는 운영자로 남는다")
    void cancelsAfterSignatureRequestUntilConclusion() throws Exception {
        List<Contract> cancelable = List.of(
                seed(ContractStatus.SIGNING),
                seed(ContractStatus.SIGNING, s -> s.brandSignedAt(now.minusHours(5)).asOf(now.minusHours(4))),
                seed(ContractStatus.CONCLUSION_PENDING));

        for (Contract c : cancelable) {
            detail(c).andExpect(jsonPath("$.permissions.canCancel").value(true));

            cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, "  " + MEMO + "  ")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELED"));

            detail(c)
                    .andExpect(jsonPath("$.contract.status").value("CANCELED"))
                    .andExpect(jsonPath("$.closure.actorType").value("ADMIN"))
                    .andExpect(jsonPath("$.closure.reasonCode").value("SCHEDULE_CHANGE"))
                    .andExpect(jsonPath("$.closure.reasonLabel").value("공구 일정 변경"))
                    .andExpect(jsonPath("$.closure.memo").value(MEMO))
                    .andExpect(jsonPath("$.closure.closedAt").exists())
                    .andExpect(jsonPath("$.permissions.canCancel").value(false))
                    .andExpect(jsonPath("$.permissions.canUpdateSignature").value(false))
                    .andExpect(jsonPath("$.permissions.canConclude").value(false));

            ContractHistory canceled = historyOf(c).getLast();
            assertThat(canceled.getEventType()).isEqualTo(ContractEventType.CANCELED);
            assertThat(canceled.getActorType()).isEqualTo(ContractActorType.ADMIN);
            assertThat(canceled.getActorDisplayName()).isEqualTo(OPERATOR_NAME);
            assertThat(canceled.getDetail()).isEqualTo("공구 일정 변경");
            // 양측 모두 서명 요청을 받은 상태였다 — 둘 다에게 알린다.
            verify(notifier).notifyBothParties(argThat(x -> x.getId().equals(c.getId())), eq("CANCELED"));
        }
    }

    @Test
    @DisplayName("운영자 취소는 브랜드·스튜디오 화면에 같은 종결로 보인다 — 주체는 운영자다")
    void operatorCancellationReachesBothSurfaces() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        cancel(c, true, ContractCloseReasonCode.NEGOTIATION_STOPPED, MEMO).andExpect(status().isOk());

        sellerDetail(c)
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.closure.actorType").value("ADMIN"))
                .andExpect(jsonPath("$.closure.reasonLabel").value("상대와 협의 중단"))
                .andExpect(jsonPath("$.closure.memo").value(MEMO))
                // 브랜드에게 계약 취소는 없다.
                .andExpect(jsonPath("$.permissions.canCancel").doesNotExist());
        creatorDetail(c)
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.closure.actorType").value("ADMIN"))
                .andExpect(jsonPath("$.closure.memo").value(MEMO))
                .andExpect(jsonPath("$.payout").doesNotExist())
                .andExpect(jsonPath("$.permissions.canDecline").value(false));
    }

    @Test
    @DisplayName("모두싸인 서명 요청 회수 확인이 없으면 취소하지 않는다 — 상대가 여전히 서명할 수 있다")
    void requiresSignatureRequestWithdrawal() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        cancel(c, null, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_CHECKLIST_REQUIRED"));
        cancel(c, false, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_CHECKLIST_REQUIRED"));

        assertUntouched(c, ContractStatus.SIGNING);
    }

    @Test
    @DisplayName("사유는 필수이고 기타면 메모도 필수다 — 양측에 그대로 전달되는 문구다")
    void validatesReasonAndMemo() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        cancel(c, true, null, MEMO).andExpect(status().isBadRequest());
        cancel(c, true, ContractCloseReasonCode.ETC, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_CANCEL_REASON_MEMO_REQUIRED"));
        cancel(c, true, ContractCloseReasonCode.ETC, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTRACT_CANCEL_REASON_MEMO_REQUIRED"));
        cancel(c, true, ContractCloseReasonCode.ETC, "가".repeat(1001)).andExpect(status().isBadRequest());
        assertUntouched(c, ContractStatus.SIGNING);

        // 기타가 아니면 메모는 선택이다.
        cancel(c, true, ContractCloseReasonCode.OUT_OF_STOCK, null).andExpect(status().isOk());
        assertThat(reload(c).getCloseReasonMemo()).isNull();
    }

    @Test
    @DisplayName("발송 전(검토 대기·반려)은 브랜드 몫이고, 체결·종결 이후는 취소 대상이 아니다")
    void refusesOutsideTheSignatureWindow() throws Exception {
        for (ContractStatus status : List.of(ContractStatus.REVIEW_PENDING, ContractStatus.REVIEW_REJECTED,
                ContractStatus.CONCLUDED, ContractStatus.EXPIRED, ContractStatus.DECLINED, ContractStatus.CANCELED)) {
            Contract c = seed(status);
            detail(c).andExpect(jsonPath("$.permissions.canCancel").value(false));
            cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTRACT_STATUS_CONFLICT"));
            assertThat(reload(c).getStatus()).isEqualTo(status);
        }
        // 작성중은 어드민에게 존재하지 않는 계약이다.
        cancel(seed(ContractStatus.DRAFT), true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("운영자 취소 API는 어드민 전용이다 — 브랜드·인플루언서 토큰은 들어오지 못한다")
    void requiresAdminRole() throws Exception {
        Contract c = seed(ContractStatus.SIGNING);

        for (String token : List.of(brandToken, creatorToken)) {
            mockMvc.perform(post("/v1/admin/contracts/" + c.getId() + "/cancel")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"signatureRequestWithdrawn\":true,\"reasonCode\":\"SCHEDULE_CHANGE\"}"))
                    .andExpect(status().isForbidden());
        }
        assertUntouched(c, ContractStatus.SIGNING);
    }

    // ── 취소 요청자(28-1 수정계획 1) ────────────────────────────────────────────

    @Test
    @DisplayName("요청자·경로·시각이 어드민 상세 cancelRequest에 그대로 나온다 — 이름은 마켓명·쇼룸명, 처리자는 취소한 운영자")
    void recordsRequesterOnDetail() throws Exception {
        LocalDateTime brandAskedAt = now.minusHours(2);
        LocalDateTime creatorAskedAt = now.minusDays(1);
        Contract byBrand = seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(10)));
        Contract byCreator = seed(ContractStatus.CONCLUSION_PENDING, s -> s.createdAt(now.minusDays(10)));
        Contract byOperator = seed(ContractStatus.SIGNING);

        cancel(byBrand, true, ContractCloseReasonCode.NEGOTIATION_STOPPED, MEMO,
                ContractActorType.SELLER, ContractCancelRequestChannel.THREAD, brandAskedAt).andExpect(status().isOk());
        cancel(byCreator, true, ContractCloseReasonCode.SCHEDULE_CHANGE, null,
                ContractActorType.CREATOR, ContractCancelRequestChannel.PHONE, creatorAskedAt).andExpect(status().isOk());
        cancel(byOperator, true, ContractCloseReasonCode.CONDITION_REVIEW, null).andExpect(status().isOk());

        JsonNode brandRequest = cancelRequestOf(byBrand);
        assertThat(brandRequest.get("requesterType").asText()).isEqualTo("SELLER");
        assertThat(brandRequest.get("requesterName").asText()).isEqualTo("글로우랩");
        assertThat(brandRequest.get("requestChannel").asText()).isEqualTo("THREAD");
        assertThat(brandRequest.get("requestChannelLabel").asText()).isEqualTo("소통 스레드");
        assertThat(at(brandRequest.get("requestedAt"))).isEqualTo(brandAskedAt);
        assertThat(brandRequest.get("processedByName").asText()).isEqualTo(OPERATOR_NAME);
        assertThat(at(brandRequest.get("processedAt")))
                .isEqualTo(reload(byBrand).getClosedAt().withNano(0));

        JsonNode creatorRequest = cancelRequestOf(byCreator);
        assertThat(creatorRequest.get("requesterType").asText()).isEqualTo("CREATOR");
        assertThat(creatorRequest.get("requesterName").asText()).isEqualTo("뷰티_하윤");
        assertThat(creatorRequest.get("requestChannelLabel").asText()).isEqualTo("전화");
        assertThat(at(creatorRequest.get("requestedAt"))).isEqualTo(creatorAskedAt);

        JsonNode operatorRequest = cancelRequestOf(byOperator);
        assertThat(operatorRequest.get("requesterType").asText()).isEqualTo("ADMIN");
        assertThat(operatorRequest.get("requesterName").isNull()).isTrue();
        assertThat(operatorRequest.get("requestChannel").isNull()).isTrue();
        assertThat(operatorRequest.get("requestedAt").isNull()).isTrue();
        assertThat(operatorRequest.get("processedByName").asText()).isEqualTo(OPERATOR_NAME);
    }

    @Test
    @DisplayName("요청자 조합이 어긋나면 400 CONTRACT_CANCEL_REQUESTER_INVALID이고 계약은 그대로다")
    void rejectsInvalidRequester() throws Exception {
        Contract c = seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(10)));
        LocalDateTime askedAt = now.minusHours(1);

        List<ResultActions> invalid = List.of(
                cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO, null, null, null),
                cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO,
                        ContractActorType.SYSTEM, null, null),
                // 브랜드·인플루언서 요청은 경로·시각이 둘 다 있어야 한다.
                cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO,
                        ContractActorType.SELLER, null, askedAt),
                cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO,
                        ContractActorType.CREATOR, ContractCancelRequestChannel.EMAIL, null),
                // 직권에 경로·시각을 보내면 버리지 않고 거절한다 — 운영자가 요청자를 잘못 고른 것이다.
                cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO,
                        ContractActorType.ADMIN, ContractCancelRequestChannel.THREAD, null),
                cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO,
                        ContractActorType.ADMIN, null, askedAt),
                // 미래 시각 · 계약 작성 이전 시각
                cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO,
                        ContractActorType.SELLER, ContractCancelRequestChannel.THREAD, LocalDateTime.now().plusHours(1)),
                cancel(c, true, ContractCloseReasonCode.SCHEDULE_CHANGE, MEMO,
                        ContractActorType.SELLER, ContractCancelRequestChannel.THREAD, now.minusDays(11)));
        for (ResultActions result : invalid) {
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONTRACT_CANCEL_REQUESTER_INVALID"));
        }
        assertUntouched(c, ContractStatus.SIGNING);
        assertThat(reload(c).getCancelRequesterType()).isNull();
    }

    @Test
    @DisplayName("운영자 취소가 아니면 cancelRequest는 null이고, 요청자는 브랜드·스튜디오 상세에 나가지 않는다")
    void requesterIsAdminOnly() throws Exception {
        detail(seed(ContractStatus.SIGNING)).andExpect(jsonPath("$.cancelRequest").value(nullValue()));
        detail(seed(ContractStatus.EXPIRED)).andExpect(jsonPath("$.cancelRequest").value(nullValue()));

        Contract c = seed(ContractStatus.SIGNING, s -> s.createdAt(now.minusDays(10)));
        cancel(c, true, ContractCloseReasonCode.NEGOTIATION_STOPPED, MEMO,
                ContractActorType.SELLER, ContractCancelRequestChannel.THREAD, now.minusHours(2)).andExpect(status().isOk());

        sellerDetail(c).andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelRequest").doesNotExist())
                .andExpect(jsonPath("$.closure.requesterType").doesNotExist());
        creatorDetail(c).andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelRequest").doesNotExist())
                .andExpect(jsonPath("$.closure.requesterType").doesNotExist());
    }

    /** 응답의 일시 형식(JacksonConfig) — 초 단위에서 자른다. */
    private static LocalDateTime at(JsonNode value) {
        return LocalDateTime.parse(value.asText(), RESPONSE_DATE_TIME);
    }

    private JsonNode cancelRequestOf(Contract c) throws Exception {
        return objectMapper.readTree(detail(c).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("cancelRequest");
    }

    private void assertUntouched(Contract c, ContractStatus expected) {
        Contract reloaded = reload(c);
        assertThat(reloaded.getStatus()).isEqualTo(expected);
        assertThat(reloaded.getClosedAt()).isNull();
        assertThat(historyOf(c)).extracting(ContractHistory::getEventType).doesNotContain(ContractEventType.CANCELED);
    }
}
