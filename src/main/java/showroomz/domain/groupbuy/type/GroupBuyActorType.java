package showroomz.domain.groupbuy.type;

/**
 * 이력·요청·응답의 주체. 운영자 호칭(어드민/운영자/실명)은 읽는 서피스가 고르므로
 * 서버는 actorType만 내리고 문구를 지어내지 않는다(§29-13 · 설계서 4-4).
 */
public enum GroupBuyActorType {
    SELLER, CREATOR, ADMIN, SYSTEM
}
