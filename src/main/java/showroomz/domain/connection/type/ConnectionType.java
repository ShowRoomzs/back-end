package showroomz.domain.connection.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ConnectionType {
    PAIR("브랜드+인플루언서"),
    OPERATOR_MARKET("운영자+브랜드"),
    OPERATOR_CREATOR("운영자+인플루언서");

    private final String description;
}
