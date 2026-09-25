package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyChangeRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyFulfillmentCheckRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.ChangeRequestStatus;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.global.config.properties.GroupBuyProperties;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 시간이 여는 전이 — 오픈 · 종료 · 무응답 자동 이행(설계서 0-5 · 3-3).
 *
 * <p>사람이 판단하는 전이는 여기 넣지 않는다 — 직권 중단 집행은 소명 기한이 지나도 운영자가 누른다.
 *
 * <p>행마다 별도 트랜잭션({@code REQUIRES_NEW})이다. 한 건 실패가 나머지를 막지 않는다. 대상 조회와 처리 사이에
 * 다른 요청이 상태를 바꿨으면 조건부 UPDATE가 0행이 되고 조용히 건너뛴다 — 중복 실행돼도 이중 전이는 없다.
 */
@Service
@RequiredArgsConstructor
public class GroupBuyLifecycleService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyExtensionRequestRepository extensionRequestRepository;
    private final GroupBuyChangeRequestRepository changeRequestRepository;
    private final GroupBuyAdminSuspensionRepository adminSuspensionRepository;
    private final GroupBuyFulfillmentCheckRepository fulfillmentCheckRepository;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final ProductGroupBuyStatusSynchronizer productSynchronizer;
    private final GroupBuyPostExposure postExposure;
    private final GroupBuyNotifier notifier;
    private final GroupBuyProperties properties;

    @Transactional(readOnly = true)
    public List<Long> findIdsToOpen(LocalDateTime now, int limit) {
        return groupBuyRepository.findIdsToOpen(now, Pageable.ofSize(limit));
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToEnd(LocalDateTime now, int limit) {
        return groupBuyRepository.findIdsToEnd(now, Pageable.ofSize(limit));
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToAutoConfirm(LocalDateTime now, int limit) {
        if (!properties.getFulfillment().isAutoConfirmOnTimeout()) {
            return List.of();
        }
        return groupBuyRepository.findIdsToAutoConfirmFulfillment(now, Pageable.ofSize(limit));
    }

    /** READY → IN_PROGRESS. 시작 버튼이 없다 — 시작 시각이 되면 시스템이 연다(§29-4). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean open(Long groupBuyId, LocalDateTime now) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId).orElse(null);
        if (groupBuy == null || groupBuy.getStatus() != GroupBuyStatus.READY || groupBuy.getStartAt().isAfter(now)) {
            return false;
        }
        if (groupBuyRepository.transition(groupBuyId, EnumSet.of(GroupBuyStatus.READY), GroupBuyStatus.IN_PROGRESS) != 1) {
            return false;
        }
        groupBuy.applyOpened(now);
        historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.OPENED, null, now);
        // 게시물 노출 시작 — 빠지면 오픈된 공구의 게시물이 소비자에게 404다(31 설계 2-9).
        postExposure.sync(groupBuy, now);
        productSynchronizer.resync(groupBuy);
        notifier.notifyBothParties(groupBuy, "OPENED");
        return true;
    }

    /**
     * IN_PROGRESS · SUSPENSION_SCHEDULED → ENDED(COMPLETED). 한 트랜잭션에서 대기 중인 것을 전부 닫는다 —
     * 연장 요청은 EXPIRED(무응답 = 변경 없이 종결), 검토 중 요청·진행 중 통지는 LAPSED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean end(Long groupBuyId, LocalDateTime now) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId).orElse(null);
        if (groupBuy == null || !groupBuy.getStatus().isSelling() || groupBuy.getEndAt().isAfter(now)) {
            return false;
        }
        // 종료 시각도 UPDATE 조건으로 다시 본다 — 대상을 고른 뒤 연장이 수락됐으면 0행이다(31 설계 5-1).
        if (groupBuyRepository.transitionIfDue(groupBuyId, GroupBuyStatus.SELLING, GroupBuyStatus.ENDED, now) != 1) {
            return false;
        }
        LocalDateTime endedAt = groupBuy.getEndAt();
        groupBuy.applyCompleted(endedAt.plusDays(properties.getFulfillment().getDueDays()));

        // 조건부 UPDATE — 인플루언서가 기한 직전에 거절했으면 0행이고 「만료」를 덮어쓰지 않는다(31 설계 5-2).
        if (extensionRequestRepository.expirePending(groupBuyId, now) > 0) {
            historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.EXTENSION_EXPIRED, null, endedAt);
        }
        changeRequestRepository.findFirstByGroupBuyIdAndStatus(groupBuyId, ChangeRequestStatus.PENDING)
                .ifPresent(request -> request.lapse(now));
        adminSuspensionRepository.findFirstByGroupBuyIdAndStatus(groupBuyId, AdminSuspensionStatus.NOTICED)
                .ifPresent(GroupBuyAdminSuspension::lapse);

        historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.ENDED, null, endedAt);
        postExposure.sync(groupBuy, now);
        productSynchronizer.resync(groupBuy);
        notifier.notifyBothParties(groupBuy, "ENDED");
        return true;
    }

    /** 무응답 자동 이행 — 스위치가 켜졌을 때만(설계서 1-9 · 기본 꺼짐). 이미 확인한 측은 건드리지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int autoConfirmFulfillment(Long groupBuyId, LocalDateTime now) {
        if (!properties.getFulfillment().isAutoConfirmOnTimeout()) {
            return 0;
        }
        GroupBuy groupBuy = groupBuyRepository.findForUpdate(groupBuyId).orElse(null);
        if (groupBuy == null || groupBuy.getStatus() != GroupBuyStatus.ENDED
                || groupBuy.getFulfillmentDueAt() == null || groupBuy.getFulfillmentDueAt().isAfter(now)) {
            return 0;
        }
        Set<FulfillmentSide> checked = fulfillmentCheckRepository.findByGroupBuyId(groupBuyId).stream()
                .map(GroupBuyFulfillmentCheck::getCheckerSide)
                .collect(Collectors.toSet());
        int created = 0;
        for (FulfillmentSide side : FulfillmentSide.values()) {
            if (checked.contains(side)) {
                continue;
            }
            fulfillmentCheckRepository.save(GroupBuyFulfillmentCheck.autoConfirmed(groupBuy, side, now));
            historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.FULFILLMENT_AUTO_CONFIRMED, side.name(), now);
            created++;
        }
        return created;
    }
}
