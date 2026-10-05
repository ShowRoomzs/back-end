package showroomz.global.delivery.tracker;

/**
 * 연동 업체가 키 단위로 조회를 막았다(사용량 초과 · 키 만료 · 키 없음) — 송장 한 건의 실패가 아니라
 * 이후 호출이 전부 실패하는 상태다. 감시 배치는 이 예외를 받으면 그 회차를 멈춘다(택배 추적 설계서 3-4).
 */
public class DeliveryTrackerBlockedException extends RuntimeException {

    public DeliveryTrackerBlockedException(String message) {
        super(message);
    }
}
