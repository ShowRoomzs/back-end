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
        Long operatorId) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public static MessageCardPayload resendRequest(Long contractId, String contractNumber, String groupBuyTitle,
                                                   String requesterType, String requesterName,
                                                   LocalDateTime requestedAt) {
        return new MessageCardPayload(contractId, contractNumber, groupBuyTitle, requesterType, requesterName,
                requestedAt, null, null, null);
    }

    public static MessageCardPayload adminCanceled(Long contractId, String contractNumber, String groupBuyTitle,
                                                   String reasonLabel, LocalDateTime processedAt, Long operatorId) {
        return new MessageCardPayload(contractId, contractNumber, groupBuyTitle, null, null, null,
                reasonLabel, processedAt, operatorId);
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
        return new MessageCardPayload(null, null, null, null, null, null, null, null, null);
    }
}
