package showroomz.domain.order.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재발송 목록 엑셀의 컬럼(35 설계서 3-4 E1) — 기본 7종 + 추가 6종. 고른 순서 = 엑셀 좌→우 열 순서.
 * 선택 컬럼 뒤에는 빈 「택배사」 「송장번호」 2열이 항상 붙는다 — 이 파일을 채워 다시 올리는 왕복이 전제다.
 */
@Getter
@RequiredArgsConstructor
public enum ClaimReshipColumn {

    CLAIM_NUMBER("접수번호", true),
    RECIPIENT("수취인", true),
    PHONE("연락처", true),
    ZIP_CODE("우편번호", true),
    ADDRESS("주소", true),
    PRODUCT_NAME("상품명", true),
    OPTION("옵션", true),
    QUANTITY("수량", false),
    RESHIP_REASON("재발송 사유", false),
    REQUESTED_AT("신청일시", false),
    ORDER_NUMBER("주문번호", false),
    GROUP_BUY_NAME("공구명", false),
    CLAIM_TYPE("유형", false);

    public static final String CARRIER_HEADER = "택배사";
    public static final String TRACKING_HEADER = "송장번호";

    private final String header;
    /** 기본 구성에 드는가. */
    private final boolean basic;
}
