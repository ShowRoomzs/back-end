package showroomz.domain.member.user.type;

/**
 * 본인인증 행 상태(57 설계서 0-7) — 포트원 3종({@code READY · VERIFIED · FAILED})에 우리 {@code CONSUMED}(Users 반영됨)를 더했다.
 * 만료는 저장하지 않고 {@code expires_at} 으로 읽을 때 판정한다.
 */
public enum IdentityVerificationStatus {
    READY,
    VERIFIED,
    FAILED,
    CONSUMED
}
