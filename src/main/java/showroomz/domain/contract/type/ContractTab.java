package showroomz.domain.contract.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 목록 탭 ↔ 상태 매핑 — 서버가 소유한다(설계서 1-3).
 *
 * <p>탭은 필터일 뿐이며 응답의 status는 항상 9종 중 하나다.
 * 목록 배지는 묶음이 아니라 개별 값 그대로 내려간다(§26-1).
 */
@Getter
@RequiredArgsConstructor
public enum ContractTab {

    ALL(Set.of(ContractStatus.values())),
    DRAFT(Set.of(ContractStatus.DRAFT)),
    REVIEW(Set.of(ContractStatus.REVIEW_PENDING, ContractStatus.REVIEW_REJECTED)),
    SIGNING(Set.of(ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING)),
    CONCLUDED(Set.of(ContractStatus.CONCLUDED)),
    CLOSED(Set.of(ContractStatus.DECLINED, ContractStatus.EXPIRED, ContractStatus.CANCELED));

    private final Set<ContractStatus> statuses;

    /** ALL은 상태 조건 자체를 걸지 않는다 — IN 절에 9종을 모두 나열할 이유가 없다. */
    public boolean isAll() {
        return this == ALL;
    }

    public static List<ContractTab> countable() {
        return List.of(values());
    }
}
