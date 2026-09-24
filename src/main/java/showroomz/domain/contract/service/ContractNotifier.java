package showroomz.domain.contract.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import showroomz.domain.contract.entity.Contract;

/**
 * 계약 상태 전이 알림 — <b>훅 자리만 두고 발송은 스텁</b>이다(설계서 5-1 · 미결 #6).
 *
 * <p>기존 푸시 인프라({@code domain/notification}의 DeviceToken·PushSender)는 앱 유저(소비자) 대상이고
 * 파트너센터·스튜디오·어드민은 전부 웹이다. 채널(메일 / 웹 알림센터 / 둘 다)이 정해지지 않은 채로
 * 만들면 버리는 코드가 된다.
 *
 * <p>호출 지점은 지금 박아 둔다 — 채널이 정해졌을 때 이 클래스 안쪽만 채우면 되고,
 * 전이 코드를 다시 훑으며 호출을 끼워 넣지 않아도 된다.
 */
@Slf4j
@Component
public class ContractNotifier {

    /** 계약 통지 채널 확정 전까지 기존 알림 포트에 양측의 호출 지점을 남긴다. */
    public void notifyBothParties(Contract contract, String event) {
        notifySeller(contract, event);
        notifyCounterparty(contract, event);
    }

    public void notifySeller(Contract contract, String event) {
        log.info("[contract-notify:stub] to=SELLER event={} contractId={} marketId={}",
                event, contract.getId(), contract.getMarket().getId());
    }

    /** 검토 요청 · 요청 취소 → 어드민. */
    public void notifyAdmin(Contract contract, String event) {
        log.info("[contract-notify:stub] to=ADMIN event={} contractId={} number={}",
                event, contract.getId(), contract.getContractNumber());
    }

    /** 거절 · 취소 · 체결 등 → 상대(인플루언서). */
    public void notifyCounterparty(Contract contract, String event) {
        log.info("[contract-notify:stub] to=CREATOR event={} contractId={} creatorId={}",
                event, contract.getId(), contract.getCreator() == null ? null : contract.getCreator().getId());
    }
}
