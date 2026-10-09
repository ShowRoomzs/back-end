package showroomz.domain.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import showroomz.api.seller.order.dto.CancelRequestRejectRequest;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.type.ResidentRegistrationNumber;
import showroomz.domain.order.type.CancelRejectReason;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.RefundTaskStatus;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.PersonalDataCipher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 1009 기획 수정본 — DB 없이 도는 규칙들(발송 기한 · 사유 · 택배사 · 주민번호 · 암호화 · 거부 사유 · 환불 상태 · 공휴일). */
@DisplayName("1009 기획 수정본 규칙")
class Plan1009PolicyTest {

    @Nested
    @DisplayName("발송 기한 = 공구 마감 + N영업일(1절)")
    class ShipDue {

        private final ShipDuePolicy policy = new ShipDuePolicy(
                new BusinessDayCalculator(new BusinessCalendar(new String[0])));

        @Test
        @DisplayName("금요일 마감 + 3영업일 = 다음 수요일의 끝")
        void fridayEnd() {
            // 2026-08-21 은 금요일이다.
            assertThat(policy.dueAt(LocalDateTime.of(2026, 8, 21, 23, 59), 3))
                    .isEqualTo(LocalDateTime.of(2026, 8, 26, 23, 59, 59));
        }

        @Test
        @DisplayName("토요일 마감은 월요일을 기산일로 본다 — + 3영업일 = 목요일의 끝")
        void saturdayEnd() {
            assertThat(policy.dueAt(LocalDateTime.of(2026, 8, 22, 12, 0), 3))
                    .isEqualTo(LocalDateTime.of(2026, 8, 27, 23, 59, 59));
        }

        @Test
        @DisplayName("공휴일이 끼면 그만큼 늦어진다")
        void holidayDelays() {
            BusinessCalendar calendar = new BusinessCalendar(new String[0]);
            calendar.replaceRegisteredHolidays(Set.of(LocalDate.of(2026, 8, 24)));
            ShipDuePolicy withHoliday = new ShipDuePolicy(new BusinessDayCalculator(calendar));

            assertThat(withHoliday.dueAt(LocalDateTime.of(2026, 8, 21, 23, 59), 3))
                    .isEqualTo(LocalDateTime.of(2026, 8, 27, 23, 59, 59));
        }

        @Test
        @DisplayName("브랜드 설정 N — 비었으면 3, 범위 밖이면 1~7로 자른다")
        void businessDaysOf() {
            Market market = new Market();
            assertThat(ShipDuePolicy.businessDaysOf(null)).isEqualTo(3);
            assertThat(ShipDuePolicy.businessDaysOf(market)).isEqualTo(3);
            market.setShippingLeadDays(10);
            assertThat(ShipDuePolicy.businessDaysOf(market)).isEqualTo(7);
            market.setShippingLeadDays(0);
            assertThat(ShipDuePolicy.businessDaysOf(market)).isEqualTo(1);
            market.setShippingLeadDays(5);
            assertThat(ShipDuePolicy.businessDaysOf(market)).isEqualTo(5);
        }

        @Test
        @DisplayName("고지 문구는 서버가 만든다")
        void notice() {
            assertThat(ShipDuePolicy.noticeText(3)).isEqualTo("공구 마감 후 3영업일 이내 발송 (주말·공휴일 제외)");
        }
    }

    @Test
    @DisplayName("등록 공휴일은 통째로 갈아 끼우고 yml 공휴일과 합쳐진다(Q1)")
    void registeredHolidays() {
        BusinessCalendar calendar = new BusinessCalendar(new String[]{"2026-12-31"});
        calendar.replaceRegisteredHolidays(Set.of(LocalDate.of(2026, 10, 9)));

        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 10, 9))).isFalse();
        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 12, 31))).isFalse();
        calendar.replaceRegisteredHolidays(Set.of());
        assertThat(calendar.isBusinessDay(LocalDate.of(2026, 10, 9))).isTrue();
    }

    @Test
    @DisplayName("반품 사유 「기타」는 반품에서만 · 소비자 귀책 · 상세 필수(5-a)")
    void otherReason() {
        assertThat(ClaimReason.OTHER.isSelectableFor(ClaimType.RETURN)).isTrue();
        assertThat(ClaimReason.OTHER.isSelectableFor(ClaimType.EXCHANGE)).isFalse();
        assertThat(ClaimReason.OTHER.isDetailRequired()).isTrue();
        assertThat(ClaimReason.OTHER.getFeeBearer().name()).isEqualTo("CONSUMER");
        assertThat(ClaimReason.SIZE_MISMATCH.isSelectableFor(ClaimType.RETURN)).isFalse();
        assertThat(ClaimReason.CHANGE_OF_MIND.isSelectableFor(ClaimType.EXCHANGE)).isTrue();
    }

    @Test
    @DisplayName("택배사 = 추적 연동 업체 목록 하나 — 쿠팡택배 제외 · 일양 · GS25 추가 · 공백 무시 매칭(5-c)")
    void carriers() {
        assertThat(DeliveryCarrier.selectable()).doesNotContain(DeliveryCarrier.COUPANG)
                .contains(DeliveryCarrier.ILYANG, DeliveryCarrier.GS25, DeliveryCarrier.CU)
                .hasSize(12);
        assertThat(DeliveryCarrier.selectable()).allMatch(carrier -> carrier.getTrackerCode() != null);
        assertThat(DeliveryCarrier.fromLabel("CU편의점택배")).isEqualTo(DeliveryCarrier.CU);
        assertThat(DeliveryCarrier.fromLabel("CU 편의점택배")).isEqualTo(DeliveryCarrier.CU);
        assertThat(DeliveryCarrier.fromLabel("GS25 편의점택배")).isEqualTo(DeliveryCarrier.GS25);
    }

    @Test
    @DisplayName("주민등록번호 — 13자리 · 하이픈 허용 · 날짜와 뒷자리 첫 숫자 검사 · 마스킹(7-a)")
    void residentNumber() {
        assertThat(ResidentRegistrationNumber.normalize("900101-1234567")).isEqualTo("9001011234567");
        assertThat(ResidentRegistrationNumber.normalize("9001011234567")).isEqualTo("9001011234567");
        assertThat(ResidentRegistrationNumber.normalize("901301-1234567")).isNull();
        assertThat(ResidentRegistrationNumber.normalize("900101-9234567")).isNull();
        assertThat(ResidentRegistrationNumber.normalize("900101-123456")).isNull();
        assertThat(ResidentRegistrationNumber.normalize(null)).isNull();
        assertThat(ResidentRegistrationNumber.mask("9001011234567")).isEqualTo("900101-1******");
    }

    @Test
    @DisplayName("고유식별정보 암호화 — 왕복 복원 · 같은 평문도 매번 다른 암호문 · 원문이 드러나지 않는다(7-a)")
    void cipher() {
        PersonalDataCipher cipher = new PersonalDataCipher("", "unit-test-secret");
        String first = cipher.encrypt("9001011234567");
        String second = cipher.encrypt("9001011234567");

        assertThat(first).isNotEqualTo(second).doesNotContain("9001011234567");
        assertThat(cipher.decrypt(first)).isEqualTo("9001011234567");
        assertThat(cipher.decrypt(second)).isEqualTo("9001011234567");
        assertThatThrownBy(() -> new PersonalDataCipher("", "other-secret").decrypt(first))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("취소 요청 거부 사유 — 코드가 없고 구 필드만 오면 기타 + 상세로 받는다(3-3)")
    void rejectReasonCompat() {
        CancelRequestRejectRequest legacy = new CancelRequestRejectRequest(null, null, "이미 출고했습니다.");
        CancelRequestRejectRequest current = new CancelRequestRejectRequest(CancelRejectReason.PICKED_UP, null, null);

        assertThat(legacy.resolvedReasonCode()).isEqualTo(CancelRejectReason.ETC);
        assertThat(legacy.resolvedDetail()).isEqualTo("이미 출고했습니다.");
        assertThat(current.resolvedReasonCode()).isEqualTo(CancelRejectReason.PICKED_UP);
        assertThat(current.resolvedDetail()).isNull();
    }

    @Test
    @DisplayName("환불 큐 — 아직 돈이 나가지 않은 상태는 대기 · 집행 중 · 실패(2-4)")
    void refundOutstanding() {
        assertThat(RefundTaskStatus.PENDING.isOutstanding()).isTrue();
        assertThat(RefundTaskStatus.EXECUTING.isOutstanding()).isTrue();
        assertThat(RefundTaskStatus.FAILED.isOutstanding()).isTrue();
        assertThat(RefundTaskStatus.DONE.isOutstanding()).isFalse();
        assertThat(RefundTaskStatus.VOID.isOutstanding()).isFalse();
    }
}
