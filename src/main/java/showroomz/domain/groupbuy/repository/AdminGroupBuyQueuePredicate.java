package showroomz.domain.groupbuy.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import showroomz.domain.groupbuy.entity.QGroupBuy;
import showroomz.domain.groupbuy.entity.QGroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.QGroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.QGroupBuyPost;
import showroomz.domain.groupbuy.type.AdminGroupBuyQueue;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 어드민 조치 큐 — <b>정의 하나를 네 곳이 쓴다</b>(32 설계 0-4): GNB 배지 · 탭 배지 · 툴바 요약 · 목록 상단 고정·경고 배경.
 * 두 벌을 두면 배지는 5인데 상단 고정 행은 4인 화면이 된다.
 *
 * <pre>
 * OPEN_REVIEW          PREPARING ∧ 게시물 PENDING
 * SUSPEND_REQUEST      PENDING 중단 요청 존재
 * EARLY_CLOSE_REQUEST  PENDING 조기 마감 요청 존재
 * APPEAL_REVIEW        NOTICED 통지 ∧ (소명 제출됨 ∨ 소명 기한 경과)
 * </pre>
 *
 * <p>네 큐는 서로 배타적이다 — 오픈 승인은 PREPARING, 요청은 READY·IN_PROGRESS에서만 생기고(공구당 PENDING 1건),
 * 통지는 검토 중 요청이 있으면 막힌다(6-2). 그래서 {@code 큐 4개의 합 == 조치 필요 탭 행 수}가 성립한다(2-3).
 *
 * <p>OPEN_REVIEW에 상태 조건을 붙이는 것이 중복처럼 보이지만 남긴다 — PENDING 게시물이 PREPARING에만 있다는 가정이
 * 30 설계의 전이 변경으로 깨져도 조건이 스스로 서 있어야 한다.
 */
public final class AdminGroupBuyQueuePredicate {

    private AdminGroupBuyQueuePredicate() {
    }

    public static BooleanExpression of(AdminGroupBuyQueue queue, QGroupBuy g, LocalDateTime now) {
        return switch (queue) {
            case OPEN_REVIEW -> openReview(g);
            case SUSPEND_REQUEST -> pendingRequest(g, ChangeRequestType.SUSPEND);
            case EARLY_CLOSE_REQUEST -> pendingRequest(g, ChangeRequestType.EARLY_CLOSE);
            case APPEAL_REVIEW -> appealReview(g, now);
        };
    }

    /** 어느 큐에든 해당 — 목록 행의 {@code actionRequired} · 「조치 필요 우선」 정렬 키 ⓪ · 조치 필요 탭. */
    public static BooleanExpression any(QGroupBuy g, LocalDateTime now) {
        return Arrays.stream(AdminGroupBuyQueue.values())
                .map(queue -> of(queue, g, now))
                .reduce(BooleanExpression::or)
                .orElseThrow();
    }

    private static BooleanExpression openReview(QGroupBuy g) {
        QGroupBuyPost post = new QGroupBuyPost("aqPost");
        return g.status.eq(GroupBuyStatus.PREPARING)
                .and(JPAExpressions.selectOne().from(post)
                        .where(post.groupBuy.eq(g), post.reviewStatus.eq(GroupBuyPostReviewStatus.PENDING))
                        .exists());
    }

    private static BooleanExpression pendingRequest(QGroupBuy g, ChangeRequestType type) {
        QGroupBuyChangeRequest request = new QGroupBuyChangeRequest("aqRequest" + type.name());
        return JPAExpressions.selectOne().from(request)
                .where(request.groupBuy.eq(g),
                        request.status.eq(ChangeRequestStatus.PENDING),
                        request.requestType.eq(type))
                .exists();
    }

    /**
     * 기한이 지난 미제출도 넣는다 — 브랜드는 더 이상 소명할 수 없고 최종 판정자는 운영자다(제17조④). 빼면 아무도
     * 누르지 않는 통지가 종료일까지 남는다(32 설계 2-3 ⚠️).
     */
    private static BooleanExpression appealReview(QGroupBuy g, LocalDateTime now) {
        QGroupBuyAdminSuspension notice = new QGroupBuyAdminSuspension("aqNotice");
        return JPAExpressions.selectOne().from(notice)
                .where(notice.groupBuy.eq(g),
                        notice.status.eq(AdminSuspensionStatus.NOTICED),
                        notice.appealSubmittedAt.isNotNull().or(notice.appealDeadlineAt.lt(now)))
                .exists();
    }
}
