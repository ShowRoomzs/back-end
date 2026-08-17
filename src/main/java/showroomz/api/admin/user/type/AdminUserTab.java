package showroomz.api.admin.user.type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.member.user.type.UserStatus;

/**
 * 소비자 목록 상태 탭 (§25-3) — 기본 진입 탭은 전체다.
 *
 * <p>{@link UserStatus#DORMANT}(휴면) 전용 탭을 두지 않은 것은 시안이 3종만 그렸기 때문이고,
 * 지금 코드 어디에서도 휴면으로 전환하는 경로가 없다(휴면 배치 미구현). 값이 생기더라도
 * {@link #ALL}에서는 보이므로 목록에서 사라지지는 않는다.
 */
@Getter
@AllArgsConstructor
public enum AdminUserTab {

    ALL("전체", null),
    ACTIVE("활성", UserStatus.NORMAL),
    SUSPENDED("정지", UserStatus.SUSPENDED),
    WITHDRAWN("탈퇴", UserStatus.WITHDRAWN);

    private final String description;

    /** null이면 상태 조건을 걸지 않는다 */
    private final UserStatus status;
}
