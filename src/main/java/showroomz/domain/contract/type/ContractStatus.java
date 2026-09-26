package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 계약 상태 9종 — 파트너·스튜디오·어드민 3서피스 공통(§25-2).
 *
 * <p>「서명 진행중」을 쪼개지 않는다. B4 · B4a · B4c는 전부 {@link #SIGNING}이고 화면 분기는
 * brandSignedAt · creatorSignedAt 두 값의 조합으로 FE가 고른다. 서버가 SIGNING_MY_TURN 같은
 * 값을 만들면 조합 4개가 상태값으로 폭발한다.
 *
 * <p>{@link #CONCLUDED} 밖으로 나가는 전이는 없다 — 체결 후 해지 값이 9종에 없고,
 * 설계서 미결 #3이 확정되기 전까지 임의로 만들지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractStatus {

    DRAFT("작성중", ContractStatusTone.NEUTRAL, false),
    REVIEW_PENDING("검토 대기", ContractStatusTone.INFO, false),
    REVIEW_REJECTED("검토 반려", ContractStatusTone.WARNING, false),
    SIGNING("서명 진행중", ContractStatusTone.INFO, false),
    CONCLUSION_PENDING("체결 처리 대기", ContractStatusTone.INFO, false),
    CONCLUDED("체결완료", ContractStatusTone.SUCCESS, true),
    DECLINED("거절", ContractStatusTone.DANGER, true),
    EXPIRED("만료", ContractStatusTone.NEUTRAL, true),
    CANCELED("취소", ContractStatusTone.NEUTRAL, true);

    private final String label;
    private final ContractStatusTone tone;
    /** 종료 상태(성공·실패 불문) — 진행 중이 아니다. */
    private final boolean closed;

    /**
     * 편집 허용 집합. §25-4의 잠금 구간(검토 대기 이후 전 구간, 검토 반려만 예외)을
     * FE 비활성이 아니라 서버 권한으로 집행한다(설계서 0-4).
     */
    public static final Set<ContractStatus> EDITABLE = Set.of(DRAFT, REVIEW_REJECTED);

    /**
     * 브랜드 삭제 허용 집합 — 아직 상대에게 나가지 않은 계약({@link #RECEIVED_BY_CREATOR} 밖)만.
     * 검토 대기는 어드민이 보고 있는 중이라 [요청 취소]로 먼저 되돌려야 한다.
     */
    public static final Set<ContractStatus> DELETABLE = Set.of(DRAFT, REVIEW_REJECTED);

    /**
     * 운영자의 [계약 취소]가 허용되는 구간 — 서명 요청 발송 이후 체결 전까지다.
     *
     * <p><b>브랜드에게는 계약 취소가 없다.</b> 발송 전의 되돌림은 [요청 취소](검토 대기 → 작성중)뿐이고,
     * 발송 이후는 모두싸인에 양측 서명 요청이 나가 있어 요청을 거둘 수 있는 운영자만 취소한다.
     *
     * <p>{@link #CONCLUDED}는 넣지 않는다 — 체결 후 해지 값이 9종에 없다(설계서 미결 #3).
     */
    public static final Set<ContractStatus> ADMIN_CANCELABLE = Set.of(SIGNING, CONCLUSION_PENDING);

    /** 재작성(이 조건으로 새 계약 작성) 허용 구간 — 진행 중 계약의 복제는 기획이 다룬 적 없다(설계서 4-3). */
    public static final Set<ContractStatus> DUPLICABLE = Set.of(CONCLUDED, DECLINED, EXPIRED, CANCELED);

    /**
     * 인플루언서에게 <b>도착한</b> 계약의 상태 집합(§27 설계서 1-1).
     *
     * <p>DRAFT · REVIEW_PENDING · REVIEW_REJECTED는 브랜드 전용이다. 이 계약들도 creator_id가
     * 이미 채워져 있으므로(§25-5-1) creator_id만으로 조회하면 브랜드가 보내지도 않은 계약이,
     * 운영자가 반려한 계약까지 인플루언서 목록에 뜬다.
     *
     * <p>가시성은 필터가 아니라 권한이다 — 이 집합을 탭 enum이 아니라 상태 enum에 두는 이유는
     * 탭이 늘거나 바뀌어도 「무엇이 도착한 계약인가」의 정의는 한 곳에 남아야 하기 때문이다.
     */
    public static final Set<ContractStatus> RECEIVED_BY_CREATOR =
            Set.of(SIGNING, CONCLUSION_PENDING, CONCLUDED, DECLINED, EXPIRED, CANCELED);

    public boolean isEditable() {
        return EDITABLE.contains(this);
    }

    public boolean isDeletable() {
        return DELETABLE.contains(this);
    }
}
