package showroomz.domain.contract.event;

/**
 * 브랜드의 검토 요청이 커밋됐다는 사실. 제출본 PDF는 이 이벤트를 받아 <b>커밋 이후에</b> 만든다.
 *
 * <p>id만 싣는다 — 수신 측은 다른 스레드에서 돌므로 여기서 넘긴 엔티티는 준영속이다. 제출 시각도 싣지 않는다.
 * 메모리의 시각은 나노초까지 있고 DB에 저장된 값은 잘려 있어 비교가 어긋난다. 수신 측이 다시 읽은 값이 기준이다.
 */
public record ContractReviewRequestedEvent(Long contractId) {
}
