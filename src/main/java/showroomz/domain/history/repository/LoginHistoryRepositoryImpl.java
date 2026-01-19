package showroomz.domain.history.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import showroomz.api.admin.history.DTO.LoginHistorySearchCondition;
import showroomz.domain.history.entity.LoginHistory;
import showroomz.domain.history.type.DeviceType;
import showroomz.domain.history.type.LoginStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static showroomz.domain.history.entity.QLoginHistory.loginHistory;
import static showroomz.domain.member.user.entity.QUsers.users;

@RequiredArgsConstructor
public class LoginHistoryRepositoryImpl implements LoginHistoryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<LoginHistory> search(LoginHistorySearchCondition condition, Pageable pageable) {
        
        List<LoginHistory> content = queryFactory
                .selectFrom(loginHistory)
                .leftJoin(loginHistory.user, users).fetchJoin() // 사용자 정보 함께 조회 (N+1 방지)
                .where(
                        betweenDate(condition),
                        eqDeviceType(condition.getDeviceType()),
                        eqCountry(condition.getCountry()),
                        eqStatus(condition.getStatus())
                )
                .orderBy(loginHistory.loginAt.desc()) // 최신순 정렬
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(loginHistory.count())
                .from(loginHistory)
                .where(
                        betweenDate(condition),
                        eqDeviceType(condition.getDeviceType()),
                        eqCountry(condition.getCountry()),
                        eqStatus(condition.getStatus())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // -- 검색 조건 Predicate 메서드들 --

    private BooleanExpression betweenDate(LoginHistorySearchCondition condition) {
        if (condition.getStartDate() == null && condition.getEndDate() == null) {
            return null;
        }
        
        LocalDateTime start = condition.getStartDate() != null ? 
                condition.getStartDate().atStartOfDay() : null;
        LocalDateTime end = condition.getEndDate() != null ? 
                condition.getEndDate().atTime(LocalTime.MAX) : null;

        if (start != null && end != null) {
            return loginHistory.loginAt.between(start, end);
        } else if (start != null) {
            return loginHistory.loginAt.goe(start);
        } else {
            return loginHistory.loginAt.loe(end);
        }
    }

    private BooleanExpression eqDeviceType(DeviceType deviceType) {
        if (deviceType == null) {
            return null;
        }

        switch (deviceType) {
            case ANDROID:
                return loginHistory.userAgent.containsIgnoreCase("Android");
            case IPHONE:
                return loginHistory.userAgent.containsIgnoreCase("iPhone");
            case DESKTOP_CHROME:
                // Edge도 내부적으로 Chrome 키워드를 포함하므로 Edge는 제외해야 순수 Chrome
                return loginHistory.userAgent.containsIgnoreCase("Chrome")
                        .and(loginHistory.userAgent.containsIgnoreCase("Edg").not())
                        .and(loginHistory.userAgent.containsIgnoreCase("Mobile").not());
            case DESKTOP_EDGE:
                return loginHistory.userAgent.containsIgnoreCase("Edg");
            default:
                return null;
        }
    }

    private BooleanExpression eqCountry(String country) {
        return (country != null && !country.isEmpty()) ? loginHistory.country.eq(country) : null;
    }

    private BooleanExpression eqStatus(LoginStatus status) {
        return status != null ? loginHistory.status.eq(status) : null;
    }
}
