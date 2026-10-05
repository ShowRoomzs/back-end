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
 *
 * <p>{@code tel} · {@code trackingUrlTemplate}은 소비자 앱 배송 조회의 [택배사 전화하기] · [택배사에서 조회]다
 * (앱 클레임 설계서 1-7) — {@code {no}}에 송장번호가 들어간다. null 이면 그 버튼을 내리지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryCarrier {

    CJ("CJ대한통운", "04", "1588-1255", "https://trace.cjlogistics.com/next/tracking.html?wblNo={no}"),
    LOTTE("롯데택배", "08", "1588-2121",
            "https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo={no}"),
    HANJIN("한진택배", "05", "1588-0011",
            "https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2={no}"),
    EPOST("우체국택배", "01", "1588-1300",
            "https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1={no}"),
    KYUNGDONG("경동택배", "23", "1899-5368", "https://kdexp.com/service/delivery/etc/delivery.do?barcode={no}"),
    DAESIN("대신택배", "22", null, null),
    LOGEN("로젠택배", "06", "1588-9988", "https://www.ilogen.com/web/personal/trace/{no}"),
    HAPDONG("합동택배", "32", null, null),
    COUPANG("쿠팡택배", null, null, null),
    WOORI("우리택배", "45", null, null),
    CU("CU편의점택배", "46", "1566-1025",
            "https://www.cupost.co.kr/postbox/delivery/localResult.cupost?invoice_no={no}");

    private final String label;
    private final String trackerCode;
    private final String tel;
    private final String trackingUrlTemplate;

    /**
     * 소비자가 회수 송장에 고를 수 있는 택배사(앱 클레임 설계서 1-7) — 시안의 목록이다. GS25 반값택배는 스마트택배
     * 코드가 확인되면 더한다(Q9). 브랜드 출고 목록(11종 전부)과는 다른 목록이다.
     */
    public boolean isConsumerSelectable() {
        return this == CJ || this == EPOST || this == HANJIN || this == LOTTE || this == LOGEN || this == CU;
    }

    /** 택배사 조회 페이지 주소 — 템플릿이 없으면 null. */
    public String trackingUrl(String trackingNumber) {
        return trackingUrlTemplate == null || trackingNumber == null
                ? null : trackingUrlTemplate.replace("{no}", trackingNumber);
    }

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
