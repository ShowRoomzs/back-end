package showroomz.domain.member.common.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.contract.service.ContractSanctionHoldService;
import showroomz.domain.member.common.type.MemberType;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

import java.util.List;

/**
 * 상대방 보류 · 재개 알림(1017 설계서 8-7 · ui-partner-15 R1 · R2 · ui-studio-13 R1 · R2) — 정지된 당사자의 <b>상대</b>에게 보낸다.
 * 정지 사실 · 사유는 문장에 넣지 않는다(「상대 계정 사정」 · 「상대 브랜드 사정」).
 *
 * <ul>
 *   <li>계약 — 검토 대기 · 서명 진행중 · 공구 생성 대기</li>
 *   <li>공구 — 오픈 전(준비중 · 준비완료)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class InboxCounterpartyHoldNotifier implements CounterpartyHoldNotifier {

    @PersistenceContext
    private EntityManager em;

    private final NotificationPublisher publisher;

    @Override
    public void holdStarted(MemberType type, Long memberId) {
        for (Object[] row : contracts(type, memberId)) {
            notifyContract(type, row, true);
        }
        for (Object[] row : groupBuys(type, memberId)) {
            notifyGroupBuy(type, row, true);
        }
    }

    @Override
    public void holdResumed(MemberType type, Long memberId, List<Long> resumedContractIds) {
        if (!resumedContractIds.isEmpty()) {
            List<Object[]> rows = em.createQuery("""
                    SELECT c.id, c.market.id, c.creator.id, c.title FROM Contract c WHERE c.id IN :ids""", Object[].class)
                    .setParameter("ids", resumedContractIds).getResultList();
            rows.forEach(row -> notifyContract(type, row, false));
        }
        for (Object[] row : groupBuys(type, memberId)) {
            // 상대도 정지 중이면 아직 보류다 — 재개 알림을 보내지 않는다.
            if (type == MemberType.BRAND ? !(Boolean) row[4] : !(Boolean) row[5]) {
                notifyGroupBuy(type, row, false);
            }
        }
    }

    private List<Object[]> contracts(MemberType type, Long memberId) {
        String party = type == MemberType.BRAND ? "c.market.id" : "c.creator.id";
        return em.createQuery("SELECT c.id, c.market.id, c.creator.id, c.title FROM Contract c WHERE " + party
                        + " = :id AND c.status IN :statuses AND c.deletedAt IS NULL AND c.creator IS NOT NULL", Object[].class)
                .setParameter("id", memberId).setParameter("statuses", ContractSanctionHoldService.HOLDABLE)
                .getResultList();
    }

    private List<Object[]> groupBuys(MemberType type, Long memberId) {
        String party = type == MemberType.BRAND ? "g.market.id" : "g.creator.id";
        return em.createQuery("SELECT g.id, g.market.id, g.creator.id, ct.title, "
                        + "(CASE WHEN g.creator.status = showroomz.domain.member.creator.type.CreatorStatus.SUSPENDED "
                        + "THEN true ELSE false END), "
                        + "(CASE WHEN g.market.status = showroomz.domain.market.type.MarketStatus.SUSPENDED "
                        + "THEN true ELSE false END) "
                        + "FROM GroupBuy g LEFT JOIN g.contract ct WHERE " + party + " = :id AND g.status IN "
                        + "(showroomz.domain.groupbuy.type.GroupBuyStatus.PREPARING, "
                        + "showroomz.domain.groupbuy.type.GroupBuyStatus.READY)", Object[].class)
                .setParameter("id", memberId).getResultList();
    }

    private void notifyContract(MemberType suspended, Object[] row, boolean started) {
        Long contractId = (Long) row[0];
        String title = row[3] == null ? "계약" : (String) row[3];
        NotificationType type = started ? NotificationType.COUNTERPARTY_HOLD_STARTED : NotificationType.COUNTERPARTY_HOLD_RESUMED;
        if (suspended == MemberType.BRAND) {
            publisher.publish(NotificationCommand.toCreator((Long) row[2], type).actorType(NotificationActorType.SYSTEM)
                    .title(started ? "상대 브랜드 사정으로 「" + title + "」 체결이 잠시 보류됐어요 · 서명 기한도 멈춰요"
                            : "「" + title + "」 체결 보류가 풀렸어요 · 남은 서명 기한부터 다시 진행돼요")
                    .targetType(NotificationTargetType.CONTRACT).targetId(contractId).build());
        } else {
            publisher.publish(NotificationCommand.toSeller((Long) row[1], type).actorType(NotificationActorType.SYSTEM)
                    .title(started ? "상대 인플루언서 계정 사정으로 「" + title + "」 체결이 보류되었습니다 — 서명 기한이 멈춥니다"
                            : "「" + title + "」 체결 보류가 해제되었습니다 — 남은 서명 기한부터 이어집니다")
                    .targetType(NotificationTargetType.CONTRACT).targetId(contractId).build());
        }
    }

    private void notifyGroupBuy(MemberType suspended, Object[] row, boolean started) {
        Long groupBuyId = (Long) row[0];
        String title = row[3] == null ? "공구" : (String) row[3];
        NotificationType type = started ? NotificationType.COUNTERPARTY_HOLD_STARTED : NotificationType.COUNTERPARTY_HOLD_RESUMED;
        if (suspended == MemberType.BRAND) {
            publisher.publish(NotificationCommand.toCreator((Long) row[2], type).actorType(NotificationActorType.SYSTEM)
                    .title(started ? "상대 브랜드 사정으로 「" + title + "」 오픈이 잠시 보류됐어요"
                            : "「" + title + "」 오픈 보류가 풀렸어요 · 일정대로 이어 가요")
                    .targetType(NotificationTargetType.GROUP_BUY).targetId(groupBuyId).build());
        } else {
            publisher.publish(NotificationCommand.toSeller((Long) row[1], type).actorType(NotificationActorType.SYSTEM)
                    .title(started ? "상대 인플루언서 계정 사정으로 「" + title + "」 오픈이 보류되었습니다"
                            : "「" + title + "」 오픈 보류가 해제되었습니다")
                    .targetType(NotificationTargetType.GROUP_BUY).targetId(groupBuyId).build());
        }
    }
}
