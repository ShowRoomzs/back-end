package showroomz.domain.groupbuy.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import showroomz.domain.groupbuy.entity.QGroupBuy;
import showroomz.domain.groupbuy.entity.QGroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.QGroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.entity.QGroupBuyPost;
import showroomz.domain.groupbuy.type.ExtensionRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;

/**
 * 스튜디오 「내 조치 필요」 — <b>정의 하나를 세 곳이 쓴다</b>(31 설계 1-3): GNB 배지 · 목록 헤더(요약 카운트) ·
 * 「내 조치 필요 먼저」 정렬 키 · 목록 행의 {@code actionRequired}. 두 벌을 두면 배지는 3인데 「먼저」 정렬에서
 * 위에 오는 건 2개인 화면이 된다.
 *
 * <pre>
 * 게시물 작성 필요       PREPARING ∧ 게시물 없음·작성중·반려        (B1 · B3)
 * 숨김 게시물 수정 필요  READY·IN_PROGRESS ∧ 승인 ∧ 숨김 중         (B5a — 고칠 권한은 인플루언서)
 * 연장 응답 필요         IN_PROGRESS ∧ end_at > now ∧ 연장 PENDING (B6)
 * 이행 확인 필요         ENDED ∧ CREATOR 확인 없음                   (B7)
 * </pre>
 *
 * <p>넣지 않는 것 — 승인대기·준비완료(공이 운영자·시간에게 있다) · 브랜드 요청 검토 중(내가 할 조치가 없다) ·
 * 직권 중단 예고(액션이 없다) · 내가 낸 요청 대기(운영자 판정 대기).
 *
 * <p>숨김 수정은 설계 표의 「비종결」 대신 <b>수정이 가능한 상태</b>로 좁힌다 — 중단 예정은 게시물 수정이 잠겨
 * (31 설계 2-5) 인플루언서가 끌 수 없는 배지가 된다.
 */
public final class CreatorGroupBuyActionPredicate {

    private CreatorGroupBuyActionPredicate() {
    }

    public static BooleanExpression of(QGroupBuy g, LocalDateTime now) {
        QGroupBuyPost writtenPost = new QGroupBuyPost("apWrittenPost");
        QGroupBuyPost hiddenPost = new QGroupBuyPost("apHiddenPost");
        QGroupBuyExtensionRequest extension = new QGroupBuyExtensionRequest("apExtension");
        QGroupBuyFulfillmentCheck check = new QGroupBuyFulfillmentCheck("apCheck");

        // 공구당 게시물은 1개다 — 「없음·작성중·반려」 = 「제출됐거나 승인된 게시물이 없다」.
        BooleanExpression postToWrite = g.status.eq(GroupBuyStatus.PREPARING)
                .and(JPAExpressions.selectOne().from(writtenPost)
                        .where(writtenPost.groupBuy.eq(g),
                                writtenPost.reviewStatus.in(GroupBuyPostReviewStatus.PENDING,
                                        GroupBuyPostReviewStatus.APPROVED))
                        .notExists());

        BooleanExpression hiddenPostToFix = g.status.in(GroupBuyStatus.READY, GroupBuyStatus.IN_PROGRESS)
                .and(JPAExpressions.selectOne().from(hiddenPost)
                        .where(hiddenPost.groupBuy.eq(g),
                                hiddenPost.reviewStatus.eq(GroupBuyPostReviewStatus.APPROVED),
                                hiddenPost.hiddenAt.isNotNull(),
                                hiddenPost.unhiddenAt.isNull().or(hiddenPost.unhiddenAt.before(hiddenPost.hiddenAt)))
                        .exists());

        BooleanExpression extensionToAnswer = g.status.eq(GroupBuyStatus.IN_PROGRESS)
                .and(g.endAt.after(now))
                .and(JPAExpressions.selectOne().from(extension)
                        .where(extension.groupBuy.eq(g), extension.status.eq(ExtensionRequestStatus.PENDING))
                        .exists());

        BooleanExpression fulfillmentToCheck = g.status.eq(GroupBuyStatus.ENDED)
                .and(JPAExpressions.selectOne().from(check)
                        .where(check.groupBuy.eq(g), check.checkerSide.eq(FulfillmentSide.CREATOR))
                        .notExists());

        return postToWrite.or(hiddenPostToFix).or(extensionToAnswer).or(fulfillmentToCheck);
    }
}
