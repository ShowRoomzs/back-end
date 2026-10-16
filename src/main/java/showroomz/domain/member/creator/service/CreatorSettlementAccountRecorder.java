package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorSettlementAccountHistory;
import showroomz.domain.member.creator.repository.CreatorSettlementAccountHistoryRepository;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.global.utils.PersonalDataCipher;

import java.time.LocalDateTime;

/**
 * 정산 계좌 변경 기록(53 설계서 1-4) — 스튜디오 계좌 변경(§50-3 · 범위 밖)이 <b>값을 바꾸기 직전</b>에 부른다. 이전 · 이후 계좌를 한 행에
 * 적고, 처리 이력에 「정산 계좌 변경 · 신한은행 → 카카오뱅크」를 남긴다(경고색 — 지급 직전 변경은 사칭 신호 · §53-1).
 *
 * <p>지금 코드에는 인플루언서 계좌 변경 경로가 없다(온보딩 최초 등록뿐) — 그 경로가 생기면 이 클래스 한 줄을 부른다.
 */
@Component
@RequiredArgsConstructor
public class CreatorSettlementAccountRecorder {

    private final CreatorSettlementAccountHistoryRepository histories;
    private final CreatorAdminHistoryRecorder adminHistory;
    private final PersonalDataCipher cipher;

    @Transactional(propagation = Propagation.MANDATORY)
    public CreatorSettlementAccountHistory recordChange(Creator creator, String newBankName, String newAccountNumber,
                                                        CreatorAdminActorType actorType, Long actorId,
                                                        LocalDateTime now) {
        String previousBank = creator.getBankName();
        String previousAccount = creator.getAccountNumber();
        CreatorSettlementAccountHistory saved = histories.save(CreatorSettlementAccountHistory.of(creator.getId(),
                previousBank, last4(previousAccount), previousAccount == null ? null : cipher.encrypt(previousAccount),
                newBankName, last4(newAccountNumber), cipher.encrypt(newAccountNumber), actorType, actorId, now));
        String detail = (previousBank == null ? "—" : previousBank) + " → " + newBankName;
        if (actorType == CreatorAdminActorType.CREATOR) {
            adminHistory.byCreator(creator.getId(), CreatorAdminEventType.SETTLEMENT_ACCOUNT_CHANGED, detail,
                    CreatorAdminHistoryRecorder.REF_SETTLEMENT_ACCOUNT, saved.getId(), now);
        } else {
            adminHistory.bySystem(creator.getId(), CreatorAdminEventType.SETTLEMENT_ACCOUNT_CHANGED, detail,
                    CreatorAdminHistoryRecorder.REF_SETTLEMENT_ACCOUNT, saved.getId(), now);
        }
        return saved;
    }

    /** 숫자만 남겨 뒤 4자리 — 없거나 짧으면 null. */
    public static String last4(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String digits = accountNumber.replaceAll("[^0-9]", "");
        return digits.length() < 4 ? null : digits.substring(digits.length() - 4);
    }
}
