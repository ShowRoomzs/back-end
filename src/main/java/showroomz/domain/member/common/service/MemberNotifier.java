package showroomz.domain.member.common.service;

import showroomz.domain.member.common.type.MemberType;

/**
 * 회원 조치 알림 포트(§51-2 「사유가 알림으로 발송된다」 · §51-5 반려 사유 통지 · 정합 계획서 R1-4) — 브랜드 · 인플루언서 조치가 이 한 곳을
 * 부른다. 발송 인프라가 생기면 구현체 하나만 바꾼다. 호출 지점은 전부 커밋 뒤({@code AfterCommit})다.
 *
 * <p>운영팀 채널의 시스템 카드(계정 정지 · 해제)는 알림이 아니다 — 조치와 같은 트랜잭션에서 따로 붙는다.
 */
public interface MemberNotifier {

    void suspended(MemberType type, Long memberId, String reasonLabel);

    void released(MemberType type, Long memberId);

    /** 탈퇴 승인 — 수신처는 파기 전에 읽은 값이다(브랜드 로그인 이메일 · 인플루언서 업무 이메일). */
    void withdrawalApproved(MemberType type, Long memberId, String recipient);

    void withdrawalRejected(MemberType type, Long memberId, String reasonLabel);

    /** 스튜디오 계좌 변경 뒤 실명 확인 실패(50 설계서 6-7). */
    void accountVerificationFailed(Long creatorId, String reasonLabel);

    /** 인플루언서 사업자 구분 변경 승인(§16-2a) — 수신은 업무 이메일. */
    void businessTypeChangeApproved(Long creatorId, String businessEmail, String requestCode);

    /** 인플루언서 사업자 구분 변경 반려(§16-5 — 정형 문구 + 상세 사유 가공 없이). */
    void businessTypeChangeRejected(Long creatorId, String businessEmail, String requestCode, String reasonLabel);

    /** 인플루언서 정산 계좌 변경 요청 승인(1021 기-22) — 새 계좌 실존 확인(PG)이 이어진다. */
    default void accountChangeApproved(Long creatorId, String requestCode) {
    }

    /** 인플루언서 정산 계좌 변경 요청 반려(1021 기-22) — 기존 계좌 그대로. */
    default void accountChangeRejected(Long creatorId, String requestCode, String reasonLabel) {
    }
}
