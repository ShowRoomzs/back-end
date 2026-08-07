package showroomz.domain.product.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductGroupBuyStatus {
    PREPARING("준비중"),
    READY("준비완료"),
    IN_PROGRESS("진행중"),
    NOT_CONNECTED("연결없음");

    private final String description;
}
