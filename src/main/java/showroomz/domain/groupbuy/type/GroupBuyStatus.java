package showroomz.domain.groupbuy.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 공구 상태 7종 — 3서피스 공통(§29-2 · 설계서 1-2).
 *
 * <p>요청·통지·게시물·이행은 상태가 아니다(설계서 0-4). B4a~B4h 11종 화면은 전부 {@link #IN_PROGRESS}이고
 * 화면 분기는 사실 테이블 조합으로 FE가 고른다.
 *
 * <p>기존 {@code ProductGroupBuyStatus}를 재사용하지 않는다 — 「연결 없음」은 상품 기준 파생값이고
 * 종료·정산완료·중단은 공구에만 있다. 두 축을 한 열에 섞지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum GroupBuyStatus {

    PREPARING("준비중", GroupBuyTone.NEUTRAL),
    READY("준비완료", GroupBuyTone.INFO),
    IN_PROGRESS("진행중", GroupBuyTone.SUCCESS),
    /** 직권 중단 통지 — 아직 팔린다. */
    SUSPENSION_SCHEDULED("중단 예정", GroupBuyTone.WARNING),
    /** 조기 마감도 여기다. 색은 §29-2 상태표·시안의 「정보」로 집행한다(설계서 7-1 #6). */
    ENDED("종료", GroupBuyTone.INFO),
    SETTLED("정산완료", GroupBuyTone.SUCCESS),
    /** 재개 불가(제16조③). */
    SUSPENDED("중단", GroupBuyTone.DANGER);

    private final String label;
    private final GroupBuyTone tone;

    /** 종결 3종 — 나가는 전이는 ENDED → SETTLED 하나뿐이다. */
    public static final Set<GroupBuyStatus> TERMINAL = Set.of(ENDED, SETTLED, SUSPENDED);

    /** 소비자가 살 수 있는 상태 — 중단 예정은 아직 팔린다. */
    public static final Set<GroupBuyStatus> SELLING = Set.of(IN_PROGRESS, SUSPENSION_SCHEDULED);

    /** 상품을 붙들고 있는 상태 — 상품 groupBuyStatus 동기화의 대상(설계서 1-11). */
    public static final Set<GroupBuyStatus> ACTIVE = Set.of(PREPARING, READY, IN_PROGRESS, SUSPENSION_SCHEDULED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isSelling() {
        return SELLING.contains(this);
    }
}
