package showroomz.domain.groupbuy.type;

/**
 * 중단·조기 마감 요청의 결과.
 *
 * <p>{@link #LAPSED} — 검토 중에 공구가 기간 만료로 끝나 판정 대상이 사라졌다. 스케줄러가 종료 전이와
 * 같은 트랜잭션에서 닫는다. 그러지 않으면 어드민 조치 큐에 이미 끝난 공구가 남는다(설계서 1-6).
 */
public enum ChangeRequestStatus {
    PENDING, APPROVED, REJECTED, LAPSED
}
