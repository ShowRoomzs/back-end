package showroomz.domain.member.common.type;

/**
 * 처리 이력의 주체 — 운영자 · 회원 본인 · 시스템. 저장값이 회원 유형마다 달라(ADMIN · BRAND · SYSTEM / ADMIN · CREATOR · SYSTEM) enum 을
 * 합치지 않고 이 인터페이스로 모양만 맞춘다(정합 계획서 R1-1).
 */
public interface AdminActorType {

    String name();

    String getLabel();

    /** 운영자 행인가 — 운영자 이름 스냅샷을 쓴다. */
    default boolean isOperator() {
        return "ADMIN".equals(name());
    }

    /** 시스템 행인가 — 처리자 이름 없이 「시스템」. */
    default boolean isSystem() {
        return "SYSTEM".equals(name());
    }
}
