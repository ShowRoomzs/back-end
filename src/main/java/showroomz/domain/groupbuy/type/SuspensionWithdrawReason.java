package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 직권 중단 철회 사유 4종(M7 · 32 설계 1-5). 코드로 받는 이유 — 철회 이력이 제25조 제재 단계와 연동될 수 있는데,
 * 「귀책이 브랜드에 없음」으로 철회된 통지를 제재 이력에 세면 안 된다. 자유 문구로는 그 구분을 할 수 없다.
 */
@Getter
@RequiredArgsConstructor
public enum SuspensionWithdrawReason {
    /** 위반은 있었다 — 제재 연동 여부 미정. */
    RECTIFIED("지적 사항이 시정 완료됨"),
    /** 제재 이력에 세지 않는다. */
    NOT_A_VIOLATION("사실관계 오인 — 위반에 해당하지 않음"),
    /** 제재 이력에 세지 않는다. */
    NOT_BRAND_FAULT("귀책이 브랜드에 없음"),
    ETC("기타(직접 입력)");

    private final String label;
}
