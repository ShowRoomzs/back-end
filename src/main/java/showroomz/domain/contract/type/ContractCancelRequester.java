package showroomz.domain.contract.type;

import java.time.LocalDateTime;

/**
 * 운영자 [계약 취소]를 누가 · 어느 경로로 · 언제 요청했는가(28-1 수정계획 1). {@code ADMIN}이면 운영자 직권이라
 * 경로·시각이 없다. 조합 검증은 요청 시각의 하한(계약 생성 시각)이 필요해 서비스가 한다.
 */
public record ContractCancelRequester(ContractActorType type, ContractCancelRequestChannel channel,
                                      LocalDateTime requestedAt) {

    public static ContractCancelRequester byAdmin() {
        return new ContractCancelRequester(ContractActorType.ADMIN, null, null);
    }
}
