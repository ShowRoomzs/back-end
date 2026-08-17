package showroomz.api.admin.user.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import showroomz.api.admin.user.AdminMemberNumber;
import showroomz.api.admin.user.type.AdminUserSort;
import showroomz.api.admin.user.type.AdminUserTab;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.history.entity.QUserStatusHistory;
import showroomz.domain.member.user.entity.QUsers;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.order.entity.QOrder;
import showroomz.domain.order.entity.QOrderProduct;
import showroomz.domain.order.type.OrderProductStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 어드민 소비자 목록 조회 (§25-3).
 *
 * <p>{@code Users} 테이블은 소비자 전용이지만 소셜 로그인 직후 약관 동의를 끝내지 않은 계정이
 * {@link RoleType#GUEST}로 먼저 저장된다. 그 행들은 아직 회원이 아니라 닉네임·이름이 비어 있으므로
 * 모든 조회에서 {@link RoleType#USER}만 남긴다 — 탭 건수와 목록이 같은 모집단을 세게 하려면
 * 이 조건이 {@code where}의 맨 앞에 있어야 한다.
 */
@Repository
@RequiredArgsConstructor
public class AdminUserQueryRepository {

    private final JPAQueryFactory queryFactory;

    private static final QUsers user = QUsers.users;
    private static final QOrder order = QOrder.order;
    private static final QOrderProduct orderProduct = QOrderProduct.orderProduct;
    private static final QUserStatusHistory statusHistory = QUserStatusHistory.userStatusHistory;

    /** 정지 탭 요약의 "최근 30일 신규 정지" 기준 */
    private static final int NEW_SUSPENSION_WINDOW_DAYS = 30;

    /**
     * 목록 한 행 — 마스킹 전 원본을 담는다. 마스킹은 서비스가 DTO로 옮기면서 한 번만 한다.
     *
     * @param orderCount 누적 주문. 취소만 남은 주문은 세지 않는다(아래 {@code orderCount()} 참고)
     */
    public record Row(
            Long userId,
            String nickname,
            String name,
            String phoneNumber,
            ProviderType providerType,
            LocalDateTime joinedAt,
            UserStatus status,
            long orderCount
    ) {}

    public Page<Row> search(AdminUserTab tab, String keyword, ProviderType providerType,
                            AdminUserSort sort, Pageable pageable) {
        BooleanBuilder where = createWhere(tab, keyword, providerType);
        NumberExpression<Long> orderCount = orderCount();

        List<Tuple> rows = queryFactory
                .select(user.id, user.nickname, user.name, user.phoneNumber,
                        user.providerType, user.createdAt, user.status, orderCount)
                .from(user)
                .leftJoin(order).on(order.user.id.eq(user.id))
                .leftJoin(orderProduct).on(orderProduct.order.id.eq(order.id)
                        .and(orderProduct.status.ne(OrderProductStatus.CANCELLED)))
                .where(where)
                .groupBy(user.id, user.nickname, user.name, user.phoneNumber,
                        user.providerType, user.createdAt, user.status)
                .orderBy(orderSpecifiers(sort, orderCount))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<Row> content = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            Long count = row.get(orderCount);
            content.add(new Row(
                    row.get(user.id),
                    row.get(user.nickname),
                    row.get(user.name),
                    row.get(user.phoneNumber),
                    row.get(user.providerType),
                    row.get(user.createdAt),
                    row.get(user.status),
                    count != null ? count : 0L
            ));
        }

        // 주문 조인은 LEFT라 행 수를 늘리지 않지만, 전체 건수는 조인 없이 세는 편이 싸고 오해도 없다
        Long total = queryFactory
                .select(user.count())
                .from(user)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /**
     * 탭 건수 — <b>상태 조건만 빼고</b> 검색어·가입 수단은 그대로 반영한다.
     *
     * <p>탭 숫자가 지금 보고 있는 필터와 같은 모집단을 세야 "전체 3 · 활성 2"를 눌렀을 때
     * 그 수만큼 나온다. 필터를 무시하고 전역 건수를 세면 탭을 옮길 때마다 숫자가 어긋난다.
     */
    public Map<UserStatus, Long> countByStatus(String keyword, ProviderType providerType) {
        List<Tuple> rows = queryFactory
                .select(user.status, user.count())
                .from(user)
                .where(createWhere(AdminUserTab.ALL, keyword, providerType))
                .groupBy(user.status)
                .fetch();

        Map<UserStatus, Long> counts = new EnumMap<>(UserStatus.class);
        for (UserStatus status : UserStatus.values()) {
            counts.put(status, 0L);
        }
        for (Tuple row : rows) {
            UserStatus status = row.get(user.status);
            Long count = row.get(user.count());
            if (status != null) {
                counts.put(status, count != null ? count : 0L);
            }
        }
        return counts;
    }

    /**
     * 정지 탭 요약의 "최근 30일 신규 정지" (§25-3).
     *
     * <p>정지 시각 컬럼이 따로 없어 상태 변경 이력에서 읽는다. <b>지금 정지 상태인 회원</b> 중
     * 최근 30일 안에 정지로 바뀐 이력이 있는 사람을 센다 — 정지됐다가 풀린 회원은 이 숫자에
     *들어가면 안 되고(지금 정지 탭에 없다), 같은 기간에 두 번 정지된 회원도 한 번만 센다.
     */
    public long countNewlySuspended(String keyword, ProviderType providerType, LocalDateTime now) {
        LocalDateTime since = now.minusDays(NEW_SUSPENSION_WINDOW_DAYS);

        Long count = queryFactory
                .select(user.countDistinct())
                .from(user)
                .join(statusHistory).on(statusHistory.user.id.eq(user.id)
                        .and(statusHistory.newStatus.eq(UserStatus.SUSPENDED))
                        .and(statusHistory.createdAt.goe(since)))
                .where(createWhere(AdminUserTab.SUSPENDED, keyword, providerType))
                .fetchOne();

        return count != null ? count : 0L;
    }

    /**
     * 누적 주문 — 취소되지 않은 주문 상품이 하나라도 있는 <b>주문의 수</b>.
     *
     * <p>주문 상품 수가 아니라 주문 수인 것은 화면이 "14건"을 주문 건수로 읽기 때문이다.
     * 전부 취소된 주문은 {@code orderProduct} 쪽이 null이 되어 {@code count(distinct ...)}에서
     * 자연히 빠진다.
     *
     * <p>취소 포함 여부는 기획 확인 대기(§25-3 확인필요 ⓐ)다. 잠정적으로 취소를 제외한다 —
     * 이 숫자는 계정 정지 판단의 근거로 쓰이므로 실제로 산 적 없는 건을 세면 안 된다.
     */
    private NumberExpression<Long> orderCount() {
        return orderProduct.order.id.countDistinct();
    }

    private OrderSpecifier<?>[] orderSpecifiers(AdminUserSort sort, NumberExpression<Long> orderCount) {
        AdminUserSort resolved = sort != null ? sort : AdminUserSort.RECENT_JOINED;
        return switch (resolved) {
            // 동점일 때 순서가 흔들리면 페이지를 넘길 때 같은 회원이 두 번 나온다 — 회원번호로 고정한다
            case ORDER_COUNT_DESC -> new OrderSpecifier<?>[]{orderCount.desc(), user.id.desc()};
            case MEMBER_NO -> new OrderSpecifier<?>[]{user.id.asc()};
            case RECENT_JOINED -> new OrderSpecifier<?>[]{user.createdAt.desc(), user.id.desc()};
        };
    }

    /**
     * 검색 축 판별 (§25-1) — 회원번호 · 닉네임 · 휴대폰 뒤 4자리 3축.
     *
     * <p>세 축을 OR로 묶지 않고 <b>입력 형태로 하나를 고른다.</b> 묶으면 "1234"가 닉네임에도
     * 걸려 무엇으로 찾은 결과인지 알 수 없고, 빈 상태 문구가 알려 주는 규칙(뒤 4자리)도 거짓이 된다.
     *
     * <p>이메일 축은 두지 않는다 — §25-4가 소셜 이메일 자체를 화면에서 뺐다(Apple 릴레이 주소는
     * 연락 가능한 주소가 아니다).
     */
    private BooleanBuilder createWhere(AdminUserTab tab, String keyword, ProviderType providerType) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(user.roleType.eq(RoleType.USER));

        AdminUserTab resolvedTab = tab != null ? tab : AdminUserTab.ALL;
        if (resolvedTab.getStatus() != null) {
            where.and(user.status.eq(resolvedTab.getStatus()));
        }

        if (providerType != null) {
            where.and(user.providerType.eq(providerType));
        }

        if (keyword == null || keyword.isBlank()) {
            return where;
        }
        String trimmed = keyword.trim();

        if (AdminMemberNumber.looksLikeMemberNumber(trimmed)) {
            Long userId = AdminMemberNumber.parseOrNull(trimmed);
            // 'CST-' 뒤가 숫자가 아니면 조건을 지우지 않고 0건으로 만든다 — 오타 하나에 전체 목록이 돌아오면 안 된다
            where.and(userId != null ? user.id.eq(userId) : user.id.isNull());
            return where;
        }

        if (isPhoneSuffix(trimmed)) {
            // 저장 형태가 '01012341234'든 '010-1234-5678'이든 뒤 4자리는 문자열 끝에 그대로 있다
            where.and(user.phoneNumber.endsWith(trimmed));
            return where;
        }

        where.and(user.nickname.containsIgnoreCase(trimmed));
        return where;
    }

    private boolean isPhoneSuffix(String keyword) {
        return keyword.length() == 4 && keyword.chars().allMatch(Character::isDigit);
    }
}
