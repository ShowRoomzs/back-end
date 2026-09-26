package showroomz.api.seller.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.seller.groupbuy.dto.GroupBuyDetailResponse;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.global.config.properties.GroupBuyProperties;

import java.time.LocalDateTime;

/**
 * 파트너 버튼 판정(설계서 4-5). <b>실행 API가 같은 메서드를 다시 호출해</b> 409로 막는다 —
 * 버튼 판정과 집행 판정이 한 메서드라 어긋날 수 없다.
 *
 * <p>시안은 결과 화면마다 버튼을 손으로 골랐고, 원칙으로 환원하면 네 칸이 한 버튼씩 넓다(설계서 4-5 ⚠️).
 * 좁힐 규칙상 근거가 기획에 없어 <b>원칙으로 집행한다</b>(7-1 #2). 좁히기로 확정되면 여기만 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyPermissionPolicy {

    private final GroupBuyProperties properties;

    public GroupBuyDetailResponse.Permissions evaluate(GroupBuyFacts facts, boolean hasPairThread, LocalDateTime now) {
        return new GroupBuyDetailResponse.Permissions(
                canConfirmStock(facts),
                canRequestExtension(facts, now),
                canRequestEarlyClose(facts),
                canRequestSuspension(facts),
                canSubmitAppeal(facts, now),
                canOpenIssue(facts),
                canCheckFulfillment(facts),
                hasPairThread);
    }

    public boolean canConfirmStock(GroupBuyFacts facts) {
        GroupBuy groupBuy = facts.groupBuy();
        return groupBuy.getStatus() == GroupBuyStatus.PREPARING && !groupBuy.isStockConfirmed();
    }

    public boolean canRequestExtension(GroupBuyFacts facts, LocalDateTime now) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.IN_PROGRESS
                && facts.extension() == null
                && isBeforeExtensionCutoff(facts.groupBuy(), now)
                && !isRequestBlocked(facts);
    }

    public boolean canRequestEarlyClose(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.IN_PROGRESS && !isRequestBlocked(facts);
    }

    /** 준비완료에서도 받는다(C4) — §30-5가 §29-6 표보다 구체적이다(설계서 7-1 #3). */
    public boolean canRequestSuspension(GroupBuyFacts facts) {
        GroupBuyStatus status = facts.groupBuy().getStatus();
        return (status == GroupBuyStatus.READY || status == GroupBuyStatus.IN_PROGRESS) && !isRequestBlocked(facts);
    }

    public boolean canSubmitAppeal(GroupBuyFacts facts, LocalDateTime now) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.SUSPENSION_SCHEDULED
                && facts.activeNotice().map(notice -> notice.isAppealOpen(now)).orElse(false);
    }

    /** 종료 또는 <b>브랜드가 요청하지 않은</b> 중단 · 열린 이슈 없음. */
    public boolean canOpenIssue(GroupBuyFacts facts) {
        GroupBuyStatus status = facts.groupBuy().getStatus();
        boolean suspendedNotByBrand = status == GroupBuyStatus.SUSPENDED
                && facts.closingChangeRequest()
                        .map(request -> request.getRequesterType() != GroupBuyActorType.SELLER)
                        .orElse(true);
        return (status == GroupBuyStatus.ENDED || suspendedNotByBrand) && facts.openIssue() == null;
    }

    public boolean canCheckFulfillment(GroupBuyFacts facts) {
        return facts.groupBuy().getStatus() == GroupBuyStatus.ENDED
                && facts.fulfillmentCheck(FulfillmentSide.SELLER).isEmpty();
    }

    /**
     * 요청 차단 조건 — 하나라도 있으면 연장·조기 마감·중단 요청이 모두 막힌다.
     * 대기 중인 연장은 차단 조건이 아니다(B4c 「중단 요청만」 가능).
     */
    public boolean isRequestBlocked(GroupBuyFacts facts) {
        return facts.pendingChangeRequest().isPresent()                                   // §29-6 검토 중 추가 요청 불가
                || facts.groupBuy().getStatus() == GroupBuyStatus.SUSPENSION_SCHEDULED    // B4i 액션은 소명뿐
                || facts.isPostHidden();                                                  // B4j 숨김이 풀린 뒤에
    }

    public boolean isBeforeExtensionCutoff(GroupBuy groupBuy, LocalDateTime now) {
        return now.isBefore(extensionCutoffAt(groupBuy));
    }

    public LocalDateTime extensionCutoffAt(GroupBuy groupBuy) {
        return groupBuy.getEndAt().minusHours(properties.getExtension().getRequestCutoffHours());
    }

    /** C1 즉시 계산용 — 연장 후 총 일수가 상한을 넘지 않는 최대 일수. */
    public int maxExtensionDays(GroupBuy groupBuy) {
        return Math.max(0, properties.getExtension().getMaxTotalDays() - groupBuy.totalDays());
    }
}
