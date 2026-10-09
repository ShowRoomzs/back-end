package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 검수 반려의 법적 근거 — 전자상거래법 제17조②(청약철회 제한) 각 호(1009 기획 수정본 5-b · 파트너 11 B1r · 소비자 C10-5).
 * 반려 사유와 함께 소비자에게 그대로 보인다.
 */
@Getter
@RequiredArgsConstructor
public enum ClaimRejectLegalBasis {
    ART17_2_1("전자상거래법 제17조②1호 · 소비자 책임으로 멸실·훼손"),
    ART17_2_2("전자상거래법 제17조②2호 · 사용·소비로 가치 현저히 감소"),
    ART17_2_3("전자상거래법 제17조②3호 · 시간 경과로 재판매 곤란"),
    ART17_2_5("전자상거래법 제17조②5호 · 복제 가능 상품 포장 훼손");

    private final String label;
}
