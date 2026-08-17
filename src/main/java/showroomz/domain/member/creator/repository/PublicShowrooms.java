package showroomz.domain.member.creator.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.member.user.type.UserStatus;

import static showroomz.domain.member.creator.entity.QCreator.creator;
import static showroomz.domain.member.user.entity.QUsers.users;

/**
 * 소비자에게 노출할 수 있는 쇼룸의 조건 (§22-1).
 *
 * <p>쇼룸 목록·상세·검색·자동완성이 <b>같은 조건</b>을 봐야 한다. 화면마다 따로 적어 두면 탈퇴한
 * 쇼룸이 한 곳에만 남는 식으로 조용히 어긋난다 — 그런 누락은 사용자가 그 쇼룸을 눌러 볼 때까지
 * 아무도 알아채지 못한다.
 *
 * <p>쇼룸명과 아이디는 등록 완료 시점에 함께 정해지므로, 둘 중 하나라도 비어 있으면 아직 등록 전이다.
 */
public final class PublicShowrooms {

    private PublicShowrooms() {
    }

    /**
     * 등록을 마쳤고(쇼룸명·아이디 확정) 소유 계정이 정상인 크리에이터.
     *
     * <p>계정 상태를 보므로 {@code creator.user}가 {@code users} 별칭으로 조인된 쿼리에서만 쓸 수 있다.
     */
    public static BooleanExpression visible() {
        return creator.showroomName.isNotNull()
                .and(creator.showroomName.ne(""))
                .and(creator.showroomAddress.isNotNull())
                .and(users.status.eq(UserStatus.NORMAL))
                .and(users.roleType.eq(RoleType.CREATOR));
    }
}
