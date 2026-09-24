package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 검토 요청을 막는 위반(설계서 2-2).
 *
 * <p>H1~H8은 기획이 확정한 하드 검증이다. 그 아래 항목들은 시안이 「필수」 표시(*)나 힌트 문구로만
 * 정해둔 것들로, <b>서버가 함께 집행</b>한다 — FE 달력·드롭다운이 보장하는 것도 서버가 다시 본다.
 * 잠금은 UI의 친절이고 규칙의 집행이 아니다. 기획이 하드 목록에 추가하면 코드는 그대로 둔다(§26-6 #6).
 *
 * <p>상품 항목의 공구가·리워드율을 필수로 본 것은 설계서 목록에 없는 서버 판단이다 —
 * 값이 빈 항목은 H1~H3을 판정할 수 없고, 가격이 없는 상품 항목이 서명된 계약서에 실릴 수는 없다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractViolationCode {

    H1("H1", ContractViolationKind.RULE, "공구가는 정가 이하여야 합니다."),
    H2("H2", ContractViolationKind.RULE, "공구가는 10원 단위로 입력해 주세요."),
    H3("H3", ContractViolationKind.RULE, "리워드율은 0~90% 사이여야 합니다."),
    H4("H4", ContractViolationKind.RULE, "공구 기간은 3일 이상 30일 이하여야 합니다."),
    H5("H5", ContractViolationKind.RULE, "공구 시작 일시는 검토 요청일로부터 7일 이후여야 합니다."),
    H6("H6", ContractViolationKind.REQUIRED, "게시 포맷·수량의 합계가 1 이상이어야 합니다."),
    H7("H7", ContractViolationKind.REQUIRED, "상품 항목을 1건 이상 추가해 주세요."),
    H8("H8", ContractViolationKind.REQUIRED, "고정 지급비 지급 조건 확인이 필요합니다."),

    TITLE_REQUIRED("TITLE_REQUIRED", ContractViolationKind.REQUIRED, "공구명을 입력해 주세요."),
    TITLE_LENGTH("TITLE_LENGTH", ContractViolationKind.RULE, "공구명은 2~40자로 입력해 주세요."),

    PERIOD_REQUIRED("PERIOD_REQUIRED", ContractViolationKind.REQUIRED, "공구 기간을 선택해 주세요."),
    PERIOD_ORDER("PERIOD_ORDER", ContractViolationKind.RULE, "공구 종료 일시는 시작 일시보다 뒤여야 합니다."),

    COUNTERPARTY_REQUIRED("COUNTERPARTY_REQUIRED", ContractViolationKind.REQUIRED, "계약 상대를 선택해 주세요."),
    COUNTERPARTY_NOT_CONNECTED("COUNTERPARTY_NOT_CONNECTED", ContractViolationKind.RULE,
            "연결됨 상태인 상대만 선택할 수 있습니다."),

    ITEM_PRODUCT_REQUIRED("ITEM_PRODUCT_REQUIRED", ContractViolationKind.REQUIRED, "상품을 선택해 주세요."),
    ITEM_PRODUCT_NOT_OWNED("ITEM_PRODUCT_NOT_OWNED", ContractViolationKind.RULE, "해당 브랜드의 상품이 아닙니다."),
    ITEM_PRODUCT_NOT_DISPLAYED("ITEM_PRODUCT_NOT_DISPLAYED", ContractViolationKind.RULE,
            "진열 상태인 상품만 선택할 수 있습니다."),
    ITEM_GROUP_BUY_PRICE_REQUIRED("ITEM_GROUP_BUY_PRICE_REQUIRED", ContractViolationKind.REQUIRED,
            "공구가를 입력해 주세요."),
    ITEM_REWARD_RATE_REQUIRED("ITEM_REWARD_RATE_REQUIRED", ContractViolationKind.REQUIRED,
            "리워드율을 입력해 주세요."),
    ITEM_REWARD_RATE_SCALE("ITEM_REWARD_RATE_SCALE", ContractViolationKind.RULE,
            "리워드율은 소수점 첫째 자리까지 입력할 수 있습니다."),

    FIXED_FEE_REQUIRED("FIXED_FEE_REQUIRED", ContractViolationKind.REQUIRED, "고정 지급비를 입력해 주세요."),
    FIXED_FEE_TRIGGER_REQUIRED("FIXED_FEE_TRIGGER_REQUIRED", ContractViolationKind.REQUIRED,
            "지급 시점을 선택해 주세요."),
    FIXED_FEE_LIMIT("FIXED_FEE_LIMIT", ContractViolationKind.RULE, "고정 지급비는 1,000만원 이하로 입력해 주세요."),

    CONTENT_DUE_DATE_REQUIRED("CONTENT_DUE_DATE_REQUIRED", ContractViolationKind.REQUIRED,
            "게시 완료 기한을 선택해 주세요."),

    SECONDARY_USE_MONTHS_REQUIRED("SECONDARY_USE_MONTHS_REQUIRED", ContractViolationKind.REQUIRED,
            "2차 활용 기간을 입력해 주세요.");

    private final String code;
    private final ContractViolationKind kind;
    private final String message;
}
