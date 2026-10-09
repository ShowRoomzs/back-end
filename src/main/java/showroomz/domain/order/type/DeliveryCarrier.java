package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 택배사 — <b>추적 연동 업체 목록 하나를 출고 · 회수 · 재발송 공통으로</b> 쓴다(거래 관리 결정 5 · 2026-10-05).
 * 자유 입력 · 「미지원 택배사」 · 수기 송장은 없다. 소비자 회수 송장과 브랜드 출고 송장이 같은 목록을 고른다.
 *
 * <p>{@code trackerCode}는 스마트택배 API 의 택배사 코드({@code t_code})다(택배 추적 설계서 2절) —
 * {@code /api/v1/companylist} 응답과 대조했다(2026-10-05 · 일양로지스 · GS25 는 2026-10-09). <b>코드가 없는 택배사는
 * 「추적 연동 업체」가 아니라 고를 수 없다</b>({@link #isSelectable()}) — 쿠팡택배는 스마트택배 목록에 없어 빠졌다.
 * enum 값은 남긴다 — 이미 등록된 송장 행을 읽어야 한다.
 *
 * <p>{@code tel} · {@code trackingUrlTemplate}은 소비자 앱 배송 조회의 [택배사 전화하기] · [택배사에서 조회]다
 * (앱 클레임 설계서 1-7) — {@code {no}}에 송장번호가 들어간다. null 이면 그 버튼을 내리지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryCarrier {

    CJ("CJ대한통운", "04", "1588-1255", "https://trace.cjlogistics.com/next/tracking.html?wblNo={no}"),
    EPOST("우체국택배", "01", "1588-1300",
            "https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1={no}"),
    HANJIN("한진택배", "05", "1588-0011",
            "https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2={no}"),
    LOTTE("롯데택배", "08", "1588-2121",
            "https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo={no}"),
    LOGEN("로젠택배", "06", "1588-9988", "https://www.ilogen.com/web/personal/trace/{no}"),
    KYUNGDONG("경동택배", "23", "1899-5368", "https://kdexp.com/service/delivery/etc/delivery.do?barcode={no}"),
    DAESIN("대신택배", "22", null, null),
    ILYANG("일양로지스", "11", "1588-0002", null),
    CU("CU 편의점택배", "46", "1566-1025",
            "https://www.cupost.co.kr/postbox/delivery/localResult.cupost?invoice_no={no}"),
    GS25("GS25 편의점택배", "24", "1577-1287", null),
    HAPDONG("합동택배", "32", null, null),
    WOORI("우리택배", "45", null, null),
    /** 스마트택배 목록에 없다 — 고를 수 없다. 기존 행 역직렬화용으로만 남긴다. */
    COUPANG("쿠팡택배", null, null, null);

    private final String label;
    private final String trackerCode;
    private final String tel;
    private final String trackingUrlTemplate;

    /** 추적 연동 업체인가 — 출고 · 회수 · 재발송 송장의 선택지는 이것 하나다. */
    public boolean isSelectable() {
        return trackerCode != null;
    }

    /** 선택지 목록 — 선언 순서(시안의 노출 순서). */
    public static List<DeliveryCarrier> selectable() {
        return Arrays.stream(values()).filter(DeliveryCarrier::isSelectable).toList();
    }

    /** 택배사 조회 페이지 주소 — 템플릿이 없으면 null. */
    public String trackingUrl(String trackingNumber) {
        return trackingUrlTemplate == null || trackingNumber == null
                ? null : trackingUrlTemplate.replace("{no}", trackingNumber);
    }

    /**
     * 엑셀 업로드의 택배사 칸 — 한글명으로 들어온다. 공백은 무시한다(「CU편의점택배」 · 「CU 편의점택배」 둘 다 받는다).
     * 못 찾으면 null.
     */
    public static DeliveryCarrier fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String compact = label.replaceAll("\\s", "");
        for (DeliveryCarrier carrier : values()) {
            if (carrier.label.replaceAll("\\s", "").equals(compact) || carrier.name().equalsIgnoreCase(compact)) {
                return carrier;
            }
        }
        return null;
    }
}
