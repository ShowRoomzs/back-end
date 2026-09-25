package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyChangeRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyFulfillmentCheckRepository;
import showroomz.domain.groupbuy.repository.GroupBuyIssueRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.type.GroupBuyIssueStatus;

/** 공구 1건의 사실 테이블을 모은다 — 상세 조립과 실행 API의 판정이 같은 스냅샷을 쓴다. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupBuyFactsLoader {

    private final GroupBuyPostRepository postRepository;
    private final GroupBuyExtensionRequestRepository extensionRequestRepository;
    private final GroupBuyChangeRequestRepository changeRequestRepository;
    private final GroupBuyAdminSuspensionRepository adminSuspensionRepository;
    private final GroupBuyFulfillmentCheckRepository fulfillmentCheckRepository;
    private final GroupBuyIssueRepository issueRepository;

    public GroupBuyFacts load(GroupBuy groupBuy) {
        Long id = groupBuy.getId();
        return new GroupBuyFacts(
                groupBuy,
                postRepository.findByGroupBuyId(id).orElse(null),
                extensionRequestRepository.findByGroupBuyId(id).orElse(null),
                changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(id),
                adminSuspensionRepository.findByGroupBuyIdOrderByNoticedAtDescIdDesc(id),
                fulfillmentCheckRepository.findByGroupBuyId(id),
                issueRepository.findFirstByGroupBuyIdAndStatus(id, GroupBuyIssueStatus.OPEN).orElse(null));
    }
}
