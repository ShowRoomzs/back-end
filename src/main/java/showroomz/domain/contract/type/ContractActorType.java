package showroomz.domain.contract.type;

/**
 * 이력·종결의 주체. 운영자 호칭(어드민/운영자/실명)은 읽는 서피스가 고르므로
 * 서버는 actorType만 내리고 문구를 지어내지 않는다(§25-9 · 설계서 1-6).
 */
public enum ContractActorType {
    SELLER, CREATOR, ADMIN, SYSTEM
}
