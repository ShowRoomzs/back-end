package showroomz.api.seller.auth.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SellerStatus {
    PENDING("승인 대기"),
    APPROVED("승인 완료"),
    REJECTED("가입 반려");

    private final String description;
}

