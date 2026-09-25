package showroomz.domain.groupbuy.service;

import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.entity.GroupBuyIssue;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentSide;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 공구 1건과 그 사실 테이블 묶음(설계서 0-4). 상태는 7종 하나이고, 요청·통지·게시물·이행은 여기서 읽는다.
 * 버튼 판정과 상세 조립이 같은 스냅샷을 보게 해 둘이 어긋나지 않게 한다.
 *
 * @param changeRequests   최신순
 * @param adminSuspensions 최신순
 */
public record GroupBuyFacts(
        GroupBuy groupBuy,
        GroupBuyPost post,
        GroupBuyExtensionRequest extension,
        List<GroupBuyChangeRequest> changeRequests,
        List<GroupBuyAdminSuspension> adminSuspensions,
        List<GroupBuyFulfillmentCheck> fulfillmentChecks,
        GroupBuyIssue openIssue
) {

    public Optional<GroupBuyChangeRequest> pendingChangeRequest() {
        return changeRequests.stream().filter(GroupBuyChangeRequest::isPending).findFirst();
    }

    /** 가장 최근에 판정된(승인·반려) 요청. 만료(LAPSED)는 판정이 아니다. */
    public Optional<GroupBuyChangeRequest> lastDecidedChangeRequest() {
        return changeRequests.stream()
                .filter(r -> r.getStatus() == ChangeRequestStatus.APPROVED || r.getStatus() == ChangeRequestStatus.REJECTED)
                .findFirst();
    }

    public Optional<GroupBuyChangeRequest> closingChangeRequest() {
        Long id = groupBuy.getClosingChangeRequestId();
        return id == null ? Optional.empty()
                : changeRequests.stream().filter(r -> Objects.equals(r.getId(), id)).findFirst();
    }

    public Optional<GroupBuyAdminSuspension> activeNotice() {
        return adminSuspensions.stream().filter(GroupBuyAdminSuspension::isNoticed).findFirst();
    }

    public Optional<GroupBuyAdminSuspension> closingAdminSuspension() {
        Long id = groupBuy.getClosingAdminSuspensionId();
        return id == null ? Optional.empty()
                : adminSuspensions.stream().filter(s -> Objects.equals(s.getId(), id)).findFirst();
    }

    public Optional<GroupBuyFulfillmentCheck> fulfillmentCheck(FulfillmentSide side) {
        return fulfillmentChecks.stream().filter(c -> c.getCheckerSide() == side).findFirst();
    }

    public boolean isPostHidden() {
        return post != null && post.isHidden();
    }
}
