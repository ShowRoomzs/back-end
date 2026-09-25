package showroomz.domain.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.repository.GroupBuyAdminSuspensionRepository;
import showroomz.domain.groupbuy.repository.GroupBuyChangeRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyExtensionRequestRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.AdminSuspensionStatus;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 공구 종결 — <b>종결 경로 5개의 부수 효과를 이 클래스 하나가 한다</b>(32 설계 0-3 · 8-1).
 *
 * <pre>
 * ① 기간 종료(스케줄러)  ENDED · COMPLETED     통지 → LAPSED
 * ② 조기 마감 승인       ENDED · EARLY_CLOSED
 * ③ 중단 요청 승인       SUSPENDED
 * ④ 직권 중단 집행       SUSPENDED             자기 자신은 호출자가 EXECUTED로
 * ⑤ 긴급 직권 중단       SUSPENDED             진행 중 통지 → SUPERSEDED
 * </pre>
 *
 * <p>경로마다 부수 효과를 구현하면 반드시 한 곳이 하나를 빠뜨리고, 빠뜨린 결과가 전부 조용하고 치명적이다 —
 * 상품 resync를 빠뜨리면 중단된 공구의 상품이 계속 결제되고, 요청 LAPSED를 빠뜨리면 어드민 조치 큐가 0이 되지 않는다.
 *
 * <p>호출자는 공구 행을 {@code PESSIMISTIC_WRITE}로 잠그고, 경로 고유의 사실(요청 APPROVED · 통지 EXECUTED)을 먼저 쓴 뒤
 * 부른다. 상태 전이의 경합 차단은 여기 조건부 UPDATE가 한다.
 */
@Component
@RequiredArgsConstructor
public class GroupBuyTerminator {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyExtensionRequestRepository extensionRequestRepository;
    private final GroupBuyChangeRequestRepository changeRequestRepository;
    private final GroupBuyAdminSuspensionRepository adminSuspensionRepository;
    private final GroupBuyHistoryRecorder historyRecorder;
    private final GroupBuyPostExposure postExposure;
    private final ProductGroupBuyStatusSynchronizer productSynchronizer;
    private final GroupBuyNotifier notifier;
    private final GroupBuyProperties properties;

    /** 판정 경로 — 상태가 이미 바뀌었으면 409다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void terminate(GroupBuy groupBuy, Termination termination, LocalDateTime now) {
        if (!tryTerminate(groupBuy, termination, now)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_STATUS_CONFLICT);
        }
    }

    /** 스케줄러 경로 — 다른 요청이 먼저 상태를 바꿨으면 조용히 false다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryTerminate(GroupBuy groupBuy, Termination t, LocalDateTime now) {
        Long id = groupBuy.getId();
        // 기간 종료는 종료 시각도 UPDATE 조건으로 다시 본다 — 대상을 고른 뒤 연장이 수락됐으면 0행이다(31 설계 5-1).
        int changed = t.requireDue()
                ? groupBuyRepository.transitionIfDue(id, t.from(), t.to(), now)
                : groupBuyRepository.transition(id, t.from(), t.to());
        if (changed != 1) {
            return false;
        }
        LocalDateTime fulfillmentDueAt = t.to() == GroupBuyStatus.ENDED
                ? t.endedAt().plusDays(properties.getFulfillment().getDueDays()) : null;
        groupBuy.applyTerminated(t.to(), t.endedAt(), t.closeType(),
                t.closingChangeRequestId(), t.closingAdminSuspensionId(), fulfillmentDueAt);

        // 대기 연장 → EXPIRED. 조건부 UPDATE — 인플루언서가 직전에 응답했으면 0행이고 응답을 덮지 않는다(31 설계 5-2).
        if (extensionRequestRepository.expirePending(id, now) > 0) {
            extensionRequestRepository.findByGroupBuyId(id).ifPresent(extension -> extension.applyExpired(now));
            historyRecorder.recordBySystem(groupBuy, GroupBuyEventType.EXTENSION_EXPIRED, null, t.endedAt());
        }
        // 검토 중 요청 → LAPSED. 빠뜨리면 조치 큐가 0이 되지 않는다.
        changeRequestRepository.findByGroupBuyIdOrderByRequestedAtDescIdDesc(id).stream()
                .filter(GroupBuyChangeRequest::isPending)
                .filter(request -> !Objects.equals(request.getId(), t.closingChangeRequestId()))
                .forEach(request -> request.lapse(now));
        // 진행 중 통지 → LAPSED 또는 SUPERSEDED. 집행 경로의 자기 자신은 호출자가 이미 EXECUTED로 바꿨다.
        adminSuspensionRepository.findByGroupBuyIdOrderByNoticedAtDescIdDesc(id).stream()
                .filter(GroupBuyAdminSuspension::isNoticed)
                .filter(notice -> !Objects.equals(notice.getId(), t.closingAdminSuspensionId()))
                .forEach(notice -> {
                    if (t.noticeFate() == AdminSuspensionStatus.SUPERSEDED) {
                        notice.supersede();
                    } else {
                        notice.lapse();
                    }
                });

        historyRecorder.record(groupBuy, t.event(), t.actor(), t.historyDetail(), t.historyRefId(), t.endedAt());
        // 게시물 노출 종료 — 빠뜨리면 끝난 공구의 게시물이 소비자 피드에 남는다(31 설계 2-9).
        postExposure.sync(groupBuy, now);
        // 상품 재동기화 — 빠뜨리면 중단된 공구의 상품이 계속 결제된다(30 설계 1-11).
        productSynchronizer.resync(groupBuy);
        notifier.notifyBothParties(groupBuy, t.event().name());
        return true;
    }

    /**
     * 종결 한 건의 명세.
     *
     * @param noticeFate      남아 있는 NOTICED 통지를 무엇으로 닫는가 — 긴급만 SUPERSEDED
     * @param requireDue      기간 종료 — {@code end_at <= now}를 UPDATE 조건에 건다
     */
    public record Termination(
            Set<GroupBuyStatus> from,
            GroupBuyStatus to,
            LocalDateTime endedAt,
            GroupBuyCloseType closeType,
            Long closingChangeRequestId,
            Long closingAdminSuspensionId,
            AdminSuspensionStatus noticeFate,
            GroupBuyEventType event,
            GroupBuyActor actor,
            String historyDetail,
            Long historyRefId,
            boolean requireDue
    ) {

        /** ① 기간 종료 — {@code endedAt = end_at}이지 now가 아니다(30 설계 3-3). */
        public static Termination completed(GroupBuy groupBuy) {
            return new Termination(GroupBuyStatus.SELLING, GroupBuyStatus.ENDED, groupBuy.getEndAt(),
                    GroupBuyCloseType.COMPLETED, null, null, AdminSuspensionStatus.LAPSED,
                    GroupBuyEventType.ENDED, GroupBuyActor.SYSTEM, null, null, true);
        }

        /** ② 조기 마감 요청 승인 — 정상 종결이라 이행 확인 기한이 생긴다. */
        public static Termination earlyClosed(Long requestId, GroupBuyActor admin, String detail, LocalDateTime now) {
            return new Termination(EnumSet.of(GroupBuyStatus.IN_PROGRESS), GroupBuyStatus.ENDED, now,
                    GroupBuyCloseType.EARLY_CLOSED, requestId, null, AdminSuspensionStatus.LAPSED,
                    GroupBuyEventType.EARLY_CLOSED, admin, detail, requestId, false);
        }

        /** ③ 중단 요청 승인 — 준비완료에서도 출발한다(30 설계 4-6 C4). */
        public static Termination suspendedByRequest(Long requestId, GroupBuyActor admin, String detail,
                                                     LocalDateTime now) {
            return new Termination(EnumSet.of(GroupBuyStatus.READY, GroupBuyStatus.IN_PROGRESS),
                    GroupBuyStatus.SUSPENDED, now, GroupBuyCloseType.SUSPENDED, requestId, null,
                    AdminSuspensionStatus.LAPSED, GroupBuyEventType.SUSPENDED, admin, detail, requestId, false);
        }

        /** ④ 직권 중단 집행 — 사전 통지 뒤에만. */
        public static Termination suspendedByNotice(Long suspensionId, GroupBuyActor admin, String detail,
                                                    LocalDateTime now) {
            return new Termination(EnumSet.of(GroupBuyStatus.SUSPENSION_SCHEDULED), GroupBuyStatus.SUSPENDED, now,
                    GroupBuyCloseType.SUSPENDED, null, suspensionId, AdminSuspensionStatus.LAPSED,
                    GroupBuyEventType.SUSPENDED_BY_ADMIN, admin, detail, suspensionId, false);
        }

        /** ⑤ 긴급 직권 중단 — 진행중·중단 예정 어디서든. 진행 중 통지는 SUPERSEDED가 된다. */
        public static Termination suspendedEmergency(Long suspensionId, GroupBuyActor admin, String detail,
                                                     LocalDateTime now) {
            return new Termination(GroupBuyStatus.SELLING, GroupBuyStatus.SUSPENDED, now,
                    GroupBuyCloseType.SUSPENDED, null, suspensionId, AdminSuspensionStatus.SUPERSEDED,
                    GroupBuyEventType.SUSPENDED_EMERGENCY, admin, detail, suspensionId, false);
        }
    }
}
