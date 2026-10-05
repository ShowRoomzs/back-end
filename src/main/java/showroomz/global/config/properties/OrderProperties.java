package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 주문·결제 운영값(결제 계획서 6-2). 잠정값(취소 수렴 간격·상한 — 9-1 ⑪)은 스테이징 관측 뒤 조정한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    /** 미결제 주문 만료 — 생성 + N분. 공구 재고는 유한하고 마감이 있다(0절). */
    private int paymentTimeoutMinutes = 30;

    /** 만료 스케줄러(1분 주기) 가동 여부. 통합 테스트는 끄고 서비스를 직접 부른다. */
    private boolean expirationSchedulerEnabled = true;

    /** 포트원 PENDING(승인 진행 중)이면 만료를 N분씩 미룬다(4-4). */
    private int expiryDeferralMinutes = 5;

    /** 미루기 상한 — 넘으면 만료하고 2-3 ③(뒤늦은 결제 자동 취소)에 맡긴다. */
    private int expiryMaxDeferrals = 3;

    /** 만료 전 포트원 조회 통신 실패 상한 — 도달하면 조회 없이 만료한다. */
    private int expiryMaxCheckFailures = 3;

    /** 취소 수렴 첫 재시도 간격 — 2·4·8·16·32분(4-4 둘째 단계). */
    private int cancelRetryBaseMinutes = 2;

    /** 취소 수렴 재시도 상한 — 도달 시 CANCEL_FAILED(운영자 처리). */
    private int cancelRetryMaxAttempts = 5;

    /** 일일 대사 05:00 KST(4-8). */
    private boolean reconciliationEnabled = true;

    /** 운영 지표 10분 주기(4-8). */
    private boolean healthCheckEnabled = true;

    /** 포트원 customer 블록에 이메일을 실을지 — 개인정보 제3자 제공 범위(9-1 ②) 법무 결과에 따라 끈다. */
    private boolean customerEmailEnabled = true;

    // ------------------------------------------------------------------ 파트너센터 주문 관리(34 설계서)

    /** 구매확정 — 배송완료 + N일 자동(약관 제19조①). 약관 개정은 배포 없이 따라간다. */
    private int purchaseConfirmDays = 7;

    /** 구매확정 배치(매시) 가동 여부. */
    private boolean purchaseConfirmSchedulerEnabled = true;

    /** 목록 조회 기간 상한(§34-2) — 초과 조회 불가. */
    private int searchRangeMaxDays = 365;

    /** 송장 엑셀 일괄 업로드 행 상한(§34-5). */
    private int shipmentUploadMaxRows = 1000;

    /** 목록 페이지 크기 상한 — 시안은 20건 고정(시안 정정 #22). 넘으면 400. */
    private int listPageSizeMax = 100;

    // ------------------------------------------------------------------ 소비자 앱 주문 내역(C10 설계서)

    /** 주문 내역 조회 범위 — 최근 N개월. 그 이전 주문의 조회 방식은 시안 미결(U7)이라 상수로 박지 않는다. */
    private int userListMonths = 6;

    /** 앱 주문 내역 페이지 크기 상한 — 넘으면 400. */
    private int userListPageSizeMax = 50;

    /** 도착 예정 — 집화일 + N배송일(일요일·공휴일 제외). 택배사별 실측 평균이 쌓이기 전의 기본값. */
    private int arrivalDefaultDays = 3;

    /** 택배사별 소요일 평균의 표본 범위 — 최근 N일의 배송완료. */
    private int arrivalStatsWindowDays = 90;

    /** 이 건수 미만인 택배사는 평균으로 보정하지 않고 기본값을 쓴다. */
    private int arrivalStatsMinSamples = 30;

    /** 평균 재집계 주기(시간). */
    private int arrivalStatsRefreshHours = 6;

    /** 발주서 1회 대상 하위주문 상한 — 넘으면 조용히 자르지 않고 400 으로 나눠 받게 한다. */
    private int purchaseOrderMaxGroups = 2000;

    // ------------------------------------------------------------------ 반품·교환(35 설계서 · 앱 클레임 설계서)

    private Claim claim = new Claim();

    /** {@code order.claim.*} — 기한·횟수는 약관·기획 미결이 많아 전부 설정값이다. 이미 발급된 기한은 소급해 움직이지 않는다. */
    @Getter
    @Setter
    public static class Claim {

        /** 기한 ① 회수 대기 방치 — 신청 + N영업일. 지나면 파트너센터에 「지연」으로 보인다(자동 취소와 다른 기한). */
        private int collectDueBusinessDays = 2;

        /** 기한 ② 검수 완료 — 입고 확인 + N영업일. */
        private int inspectDueBusinessDays = 2;

        /** 기한 ②의 기산점 — RECEIVED(입고 확인 · 시안) | ARRIVED(추적상 도착 · 약관 제20조①). §35-9 A-5 확정 대기. */
        private String inspectDueBasis = "RECEIVED";

        /** 회수 송장 등록 기한 — 접수 + N일의 끝. 지나면 요청이 자동 취소된다. 송장 수정 기한도 같다. */
        private int invoiceDueDays = 7;

        /** 반려 상품 재발송비 결제 기한 — 판정 종료 + N일. 지나면 미결제 고지가 시작된다. */
        private int reshipPayDueDays = 14;

        /** 교환 재발송비 결제 대기 — N분 안에 결제되지 않으면 요청 초안을 지운다. */
        private int paymentPendingMinutes = 30;

        /** 보관 기한이 생기는 최소 고지 횟수. */
        private int storageRequiredNotices = 2;

        /** 보관 기간 — 최종 고지일 + N개월. */
        private int storageMonths = 3;

        /** 검수 거절 증빙 사진 상한. */
        private int evidenceMax = 5;

        /** 추적상 도착 전에도 입고 확인을 받을지 — 택배 추적이 꺼져 있는 동안의 출구(35 설계서 0-7). */
        private boolean receiveBeforeArrival = true;

        /** 회수 송장 미등록 자동 취소 배치 가동 여부. */
        private boolean invoiceExpirySchedulerEnabled = true;
    }
}
