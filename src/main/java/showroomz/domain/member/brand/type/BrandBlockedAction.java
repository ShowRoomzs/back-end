package showroomz.domain.member.brand.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 정지 중 막히는 행위(§51-1 「새로 벌이는 일만 차단」) — 가드가 409 메시지에 실어 무엇이 막혔는지 알린다. */
@Getter
@RequiredArgsConstructor
public enum BrandBlockedAction {
    PRODUCT_CREATE("상품을 등록할"),
    CONTRACT_CREATE("새 계약을 작성할"),
    CONTRACT_REVIEW_REQUEST("계약 검토를 요청할"),
    CONNECTION_REQUEST("인플루언서에게 연결을 요청할"),
    GROUP_BUY_OPEN("공구를 오픈할");

    private final String phrase;

    public String message() {
        return "계정 이용 정지 중에는 " + phrase + " 수 없습니다. 운영팀 소통 스레드로 문의해 주세요.";
    }
}
