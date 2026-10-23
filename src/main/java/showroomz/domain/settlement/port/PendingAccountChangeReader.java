package showroomz.domain.settlement.port;

/**
 * 인플루언서 정산 계좌 변경 요청이 확인 중인지(1021 결정 노트 기-22 · 설계서 8-0 ②) — 정산이 변경 요청을 <b>읽는</b> 방향의 포트다.
 * 구현은 변경 요청 모듈({@code CreatorPendingAccountChangeReader}).
 *
 * <p>요청 중에는 계좌 확인 상태가 기존 값 그대로라, 이 포트 없이는 보류 재판정({@code PayoutBlockPolicy})이 확인 전에 보류를 풀어 기존 계좌로
 * 지급한다.
 */
public interface PendingAccountChangeReader {

    boolean hasPending(Long creatorId);
}
