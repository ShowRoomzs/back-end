package showroomz.domain.message.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 스레드 참가자·메시지 발신자 구분. SELLER/CREATOR는 각각 Market.id/Creator.id를,
 * ADMIN은 Seller.id(운영자 계정도 SELLER 테이블에 roleType=ADMIN으로 저장됨)를 참조한다.
 */
@Getter
@AllArgsConstructor
public enum ParticipantType {
    SELLER("브랜드"),
    CREATOR("인플루언서"),
    ADMIN("운영자");

    private final String description;
}
