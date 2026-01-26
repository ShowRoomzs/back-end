package showroomz.domain.member.user.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WithdrawalReason {
    INCONVENIENT_USE("앱 사용이 불편해요"),
    DIFFICULT_SEARCH("상품 탐색이 어려워요"),
    ETC("기타 (직접 입력)"); // 나중에 직접 입력이 생길 경우를 대비

    private final String description;
}
