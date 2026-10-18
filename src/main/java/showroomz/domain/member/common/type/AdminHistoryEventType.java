package showroomz.domain.member.common.type;

/**
 * 처리 이력 사건 — 라벨 · 톤 · 저장 여부. 저장 사건은 회원 유형별 이력 테이블에 쓰고, 파생 사건(가입 신청 · 입점 승인 · 변경 요청)은 원천
 * 테이블을 읽을 때 합친다 — 다시 쓰면 백필과 이중 기록이 생긴다(52 설계서 0-6 · 53 설계서 12 #5 · 정합 계획서 R1-8).
 */
public interface AdminHistoryEventType {

    String name();

    String getLabel();

    String getTone();

    /** 이 모듈이 저장하는 사건 — false 는 읽기 전용 파생 행. */
    boolean isStored();
}
