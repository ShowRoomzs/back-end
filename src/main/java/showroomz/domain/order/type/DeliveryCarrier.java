package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 택배사 11종 확정 · 사용 빈도순(§34-5 rev.4) — 자유 입력·「미지원 택배사」 없음.
 * 연동 지원 택배사만 노출되므로 추적 불가 송장이 애초에 등록되지 않는다.
 *
 * <p>{@code trackerCode}는 연동 업체의 택배사 코드다 — 스펙 확정(§34-13 #16) 전까지 null 로 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryCarrier {

    CJ("CJ대한통운", null),
    LOTTE("롯데택배", null),
    HANJIN("한진택배", null),
    EPOST("우체국택배", null),
    KYUNGDONG("경동택배", null),
    DAESIN("대신택배", null),
    LOGEN("로젠택배", null),
    HAPDONG("합동택배", null),
    COUPANG("쿠팡택배", null),
    WOORI("우리택배", null),
    CU("CU편의점택배", null);

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
