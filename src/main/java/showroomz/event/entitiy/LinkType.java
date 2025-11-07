package showroomz.event.entitiy;
public enum LinkType {
    PRODUCT_LIST,   // 특정 상품 목록 (필터, 정렬 포함)
    PRODUCT_DETAIL, // (추후 확장 대비) 특정 상품 상세
    EXTERNAL_URL,   // 외부 웹사이트 링크
    INTERNAL_PAGE,  // 앱/웹 내부 페이지 (쿠폰함 등)
    EVENT_DETAIL,   // (추후 확장 대비) 이벤트 상세 페이지
    NONE            // 링크 없음 (이미지 클릭 반응 없음)
}