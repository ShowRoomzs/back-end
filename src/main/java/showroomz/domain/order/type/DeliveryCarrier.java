package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 택배사 11종 확정 · 사용 빈도순(§34-5 rev.4) — 자유 입력·「미지원 택배사」 없음.
 * 연동 지원 택배사만 노출되므로 추적 불가 송장이 애초에 등록되지 않는다.
 *
 * <p>{@code trackerCode}는 스마트택배 API 의 택배사 코드({@code t_code})다(택배 추적 설계서 2절) —
 * {@code /api/v1/companylist} 응답과 대조했다(2026-10-05). null 이면 그 택배사는 추적·형식 검증이 되지 않는다.
 * <b>쿠팡택배는 스마트택배 목록에 없다</b> — enum 에서 뺄지는 기획 확인 대기다(설계서 7절 #3).
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryCarrier {

    CJ("CJ대한통운", "04"),
    LOTTE("롯데택배", "08"),
    HANJIN("한진택배", "05"),
    EPOST("우체국택배", "01"),
    KYUNGDONG("경동택배", "23"),
    DAESIN("대신택배", "22"),
    LOGEN("로젠택배", "06"),
    HAPDONG("합동택배", "32"),
    COUPANG("쿠팡택배", null),
    WOORI("우리택배", "45"),
    CU("CU편의점택배", "46");

    private final String label;
    private final String trackerCode;

    /** 엑셀 업로드의 택배사 칸 — 한글명으로 들어온다. 못 찾으면 null. */
    public static DeliveryCarrier fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        for (DeliveryCarrier carrier : values()) {
            if (carrier.label.equals(trimmed) || carrier.name().equalsIgnoreCase(trimmed)) {
                return carrier;
            }
        }
        return null;
    }
}
