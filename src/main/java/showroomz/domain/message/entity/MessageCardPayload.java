package showroomz.domain.message.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;

/**
 * 시스템 카드의 생성 시점 스냅샷(36 설계 0-3) — 나중에 원본이 바뀌어도 「그때 그 안내」가 그대로 남는다.
 *
 * <p>운영자는 <b>id만</b> 담는다. 이름을 여기에 넣으면 상대 서피스 응답으로 새어 나갈 길이 생긴다 —
 * 이름은 어드민 직렬화가 붙인다(0-4). 액션 상태(재발송 알림 전/후)도 담지 않는다 — 참조 객체에서 읽는다.
 *
 * <p>정산 조정 협의 카드(44 이슈 스레드 설계서 5-2)는 {@link #adjustment}에 사실을 담는다 — 브랜드명 · 쇼룸명은 양측이 서로 보는 값이라
 * 넣는다(운영자 이름 · id 는 어느 조정 카드에도 없다). 구 카드는 이 필드가 없어 그대로 읽힌다(NON_NULL · 모르는 필드 무시).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageCardPayload(
        Long contractId,
        String contractNumber,
        String groupBuyTitle,
        String requesterType,
        String requesterName,
        LocalDateTime requestedAt,
        String reasonLabel,
        LocalDateTime processedAt,
        Long operatorId,
        Adjustment adjustment,
        Sanction sanction,
        Notice notice) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * 정산 조정 카드의 스냅샷 — 카드 종류마다 쓰는 필드가 다르다(요청 · 제안 = 제안자 · 금액 · 사유 / 동의 · 반대 = 응답자 / 결과 = 확정 금액).
     *
     * @param agreedByType 합의 카드의 동의한 쪽 — SELLER · CREATOR
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Adjustment(
            Long settlementId,
            String settlementNumber,
            Long groupBuyId,
            Long adjustmentId,
            Long proposalId,
            Integer seq,
            String proposerType,
            String proposerName,
            Long originalRewardAmount,
            Long rewardAmount,
            String reason,
            LocalDateTime deadlineAt,
            String responderType,
            String responderName,
            LocalDateTime respondedAt,
            Long finalRewardAmount,
            String agreedByType,
            String agreedByName,
            LocalDateTime closedAt) {
    }

    public static MessageCardPayload resendRequest(Long contractId, String contractNumber, String groupBuyTitle,
                                                   String requesterType, String requesterName,
                                                   LocalDateTime requestedAt) {
        return new MessageCardPayload(contractId, contractNumber, groupBuyTitle, requesterType, requesterName,
                requestedAt, null, null, null, null, null, null);
    }

    public static MessageCardPayload adminCanceled(Long contractId, String contractNumber, String groupBuyTitle,
                                                   String reasonLabel, LocalDateTime processedAt, Long operatorId) {
        return new MessageCardPayload(contractId, contractNumber, groupBuyTitle, null, null, null,
                reasonLabel, processedAt, operatorId, null, null, null);
    }

    /**
     * 동의 확인 · 해지 결과 · 직권 중단 사전 통지 카드(1021 설계서 1-6 · 2-3) — 버튼이 없는 안내 카드다. 동의 · 소명은 답글로 받고 결정은
     * 각 관리 화면에서 한다(어드민 20a A8).
     */
    public static MessageCardPayload notice(Long contractId, String contractNumber, String groupBuyTitle,
                                            String requesterType, String requesterName, LocalDateTime requestedAt,
                                            String reasonLabel, LocalDateTime processedAt, Long operatorId,
                                            Notice notice) {
        return new MessageCardPayload(contractId, contractNumber, groupBuyTitle, requesterType, requesterName,
                requestedAt, reasonLabel, processedAt, operatorId, null, null, notice);
    }

    /**
     * 안내 카드의 사실 — 카드 종류마다 채우는 칸이 다르다.
     *
     * @param kind               CANCEL(계약 취소 동의) · TERMINATE(계약 해지 동의 · 해지 결과) · ADMIN_SUSPENSION(직권 중단 사전 통지)
     * @param body               당사자에게 그대로 보이는 설명(요청 메모 · 해지 설명 · 통지 본문)
     * @param groupBuyClosed     [해지 결과] 생성된 공구가 함께 종결됐는가
     * @param reasonClause       [직권 중단] 근거 호(ART17_1_LAW 등)
     * @param counterpartyNoticed [직권 중단] 상대 당사자에게도 같은 통지가 갔는가
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Notice(String kind, String body, Boolean groupBuyClosed, Long groupBuyId, String groupBuyNumber,
                         String reasonClause, String reasonClauseLabel, LocalDateTime executeScheduledAt,
                         LocalDateTime appealDeadlineAt, Boolean counterpartyNoticed) {
    }

    /**
     * 계정 정지 · 해제 카드(52 설계서 8-1) — 사유 문장 · 처리 시각 · 운영자 id 만. 기존 결과 카드 필드를 그대로 쓴다(카드 읽기 경로가 같다).
     * 계약 필드는 비어 있다.
     */
    public static MessageCardPayload sanction(String reasonLabel, LocalDateTime processedAt, Long operatorId) {
        return new MessageCardPayload(null, null, null, null, null, null, reasonLabel, processedAt, operatorId, null, null,
                null);
    }

    /**
     * 계정 정지 · 해제 카드(1017 설계서 8-9) — 기존 한 문장({@code reasonLabel})에 더해 사유 코드 · 라벨 · 상세 · 지급 정지 여부를 나눠 담는다.
     * 카드가 「사유 · 상세 사유 · 차단 · 계속 이용 · 정산 줄 · 바로가기」를 그린다(ui-partner-15 S7 · ui-studio-13 S8). 차단 · 계속 이용
     * 목록은 회원 유형별 고정 문구라 FE 가 {@code memberType}으로 고른다. 옛 카드는 이 필드가 없어 한 줄로 읽힌다.
     */
    public static MessageCardPayload sanction(String reasonText, LocalDateTime processedAt, Long operatorId,
                                              Sanction sanction) {
        return new MessageCardPayload(null, null, null, null, null, null, reasonText, processedAt, operatorId, null,
                sanction, null);
    }

    /**
     * @param memberType    BRAND · CREATOR
     * @param reasonCode    정지 사유 코드(정지 카드) · 해제 사유 코드(해제 카드)
     * @param reasonDetail  운영자가 적은 상세 · 해제 메모 — 없으면 null
     * @param payoutHeld    정지 카드 — 지급 정지 사유인가(정산 줄 문구가 갈린다)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sanction(String memberType, String reasonCode, String reasonLabel, String reasonDetail,
                           Boolean payoutHeld, java.time.LocalDate endsOn, java.time.LocalDateTime appealDeadlineAt) {

        /** 기간 없는 카드(해제 카드 · 1021 이전 정지). */
        public Sanction(String memberType, String reasonCode, String reasonLabel, String reasonDetail,
                        Boolean payoutHeld) {
            this(memberType, reasonCode, reasonLabel, reasonDetail, payoutHeld, null, null);
        }
    }

    public static MessageCardPayload settlementAdjustment(Long contractId, String contractNumber, String groupBuyTitle,
                                                          Adjustment adjustment) {
        return new MessageCardPayload(contractId, contractNumber, groupBuyTitle, null, null, null, null, null, null,
                adjustment, null, null);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("카드 스냅샷 직렬화 실패", e);
        }
    }

    /** 스냅샷이 비었거나 읽을 수 없으면 빈 값으로 본다 — 카드 한 장 때문에 대화 조회 전체가 실패하면 안 된다. */
    public static MessageCardPayload parse(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            return MAPPER.readValue(json, MessageCardPayload.class);
        } catch (Exception e) {
            return empty();
        }
    }

    private static MessageCardPayload empty() {
        return new MessageCardPayload(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
