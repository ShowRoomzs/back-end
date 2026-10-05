package showroomz.domain.message.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 시스템 카드 스냅샷(36 설계 0-3) — 그때의 사실을 그대로 되읽고, 운영자는 id로만 담긴다. */
class MessageCardPayloadTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 14, 10, 40, 12);

    @Test
    @DisplayName("재발송 요청 스냅샷은 저장한 값 그대로 되읽힌다 — 시각도 초 단위까지 보존된다")
    void resendRequestRoundTrip() {
        MessageCardPayload payload = MessageCardPayload.resendRequest(41L, "CTR-20260813-041", "겨울 리페어 크림 공구",
                "CREATOR", "뷰티_소연", AT);

        assertThat(MessageCardPayload.parse(payload.toJson())).isEqualTo(payload);
    }

    @Test
    @DisplayName("직권 취소 스냅샷의 운영자는 id뿐이다 — 이름이 JSON에 들어가지 않는다")
    void adminCanceledHoldsOperatorIdOnly() {
        MessageCardPayload payload = MessageCardPayload.adminCanceled(41L, "CTR-20260813-034", "여름 수분 세럼 공구",
                "공구 일정 변경 — 조건 재협의", AT, 3L);
        String json = payload.toJson();

        assertThat(MessageCardPayload.parse(json)).isEqualTo(payload);
        assertThat(json).contains("\"operatorId\":3").doesNotContain("Name\":\"김");
    }

    @Test
    @DisplayName("값이 없는 필드는 JSON에 쓰지 않는다")
    void omitsNullFields() {
        String json = MessageCardPayload.resendRequest(41L, "CTR-1", null, "SELLER", "글로우랩", AT).toJson();

        assertThat(json).doesNotContain("groupBuyTitle", "reasonLabel", "operatorId", "processedAt");
    }

    @Test
    @DisplayName("비었거나 깨진 스냅샷은 빈 값으로 읽는다 — 카드 한 장 때문에 대화 조회가 실패하지 않는다")
    void brokenSnapshotReadsAsEmpty() {
        for (String json : new String[]{null, "", "   ", "{not-json", "[]"}) {
            MessageCardPayload parsed = MessageCardPayload.parse(json);
            assertThat(parsed.contractId()).as(String.valueOf(json)).isNull();
            assertThat(parsed.contractNumber()).isNull();
        }
    }

    @Test
    @DisplayName("모르는 필드가 섞인 스냅샷도 읽는다 — 카드 형식이 늘어도 옛 행이 깨지지 않는다")
    void ignoresUnknownFields() {
        MessageCardPayload parsed = MessageCardPayload.parse("{\"contractId\":41,\"futureField\":\"x\"}");

        assertThat(parsed.contractId()).isEqualTo(41L);
    }
}
