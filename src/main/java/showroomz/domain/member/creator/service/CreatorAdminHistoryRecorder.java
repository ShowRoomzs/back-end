package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.creator.entity.CreatorAdminHistory;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.member.creator.type.CreatorAdminEventType;

import java.time.LocalDateTime;

/**
 * 인플루언서 처리 이력 기록(53 설계서 0-7) — 조치와 <b>같은 트랜잭션</b>에서 쓴다(조치가 롤백되면 이력도 없다). 파생 사건(가입 신청 ·
 * 입점 승인)은 저장하지 않는다 — 읽을 때 합친다.
 */
@Component
@RequiredArgsConstructor
public class CreatorAdminHistoryRecorder {

    public static final String REF_SANCTION = "SANCTION";
    public static final String REF_WITHDRAWAL_REQUEST = "WITHDRAWAL_REQUEST";
    public static final String REF_MEMO = "MEMO";
    public static final String REF_PRIVACY_ACCESS = "PRIVACY_ACCESS";
    public static final String REF_SETTLEMENT_ACCOUNT = "SETTLEMENT_ACCOUNT";

    private final CreatorAdminHistoryRepository histories;

    @Transactional(propagation = Propagation.MANDATORY)
    public CreatorAdminHistory byOperator(Long creatorId, CreatorAdminEventType eventType, Long operatorId,
                                          String operatorName, String detail, String refType, Long refId,
                                          LocalDateTime now) {
        return histories.save(CreatorAdminHistory.of(creatorId, eventType, CreatorAdminActorType.ADMIN, operatorId,
                operatorName, detail, refType, refId, now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CreatorAdminHistory byCreator(Long creatorId, CreatorAdminEventType eventType, String detail,
                                         String refType, Long refId, LocalDateTime now) {
        return histories.save(CreatorAdminHistory.of(creatorId, eventType, CreatorAdminActorType.CREATOR, null, null,
                detail, refType, refId, now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CreatorAdminHistory bySystem(Long creatorId, CreatorAdminEventType eventType, String detail,
                                        String refType, Long refId, LocalDateTime now) {
        return histories.save(CreatorAdminHistory.of(creatorId, eventType, CreatorAdminActorType.SYSTEM, null, null,
                detail, refType, refId, now));
    }
}
