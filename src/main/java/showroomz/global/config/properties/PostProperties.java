package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게시물 운영 정책값 (§24-8 미결 항목 — 법률 자문 대기).
 *
 * <p>전부 <b>설정값으로 빼는</b> 이유 — 기획서가 이 값들을 "가정값"이라고 명시했다. 하드코딩하면
 * 확정될 때마다 코드를 고쳐야 하고, 그 사이 스테이징과 운영이 다른 기한으로 동작할 수 있다.
 *
 * <p>보관 기간류는 <b>짧게 잡았다가 늘리기가 불가능</b>하다. 이미 지운 데이터는 돌아오지 않는다.
 * 그래서 미확정 구간의 기본값은 전부 "지우지 않는 쪽"이다 — 파기 배치는 꺼져 있고
 * ({@link #purgeEnabled} = false), 노출 로그 보관 기간은 0(무기한)이다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "post")
public class PostProperties {

    /** 이의 신청 기한 — 시안 7일 (§24-8 ⓐ) */
    private int appealDeadlineDays = 7;

    /** 심사 예상 소요 — 표시용이다. 로직에 쓰지 않는다 (§24-8 ⓑ) */
    private int appealReviewBusinessDays = 3;

    /** 반려 후 원본 내려받기 유예 (§24-8 ⓒ) */
    private int downloadGraceDays = 30;

    /** 삭제 게시물 비공개 보관 기간 (§24-6 · §24-8 ⓓ) */
    private int retentionMonths = 6;

    /**
     * 파기 배치 실행 여부. 기본값은 <b>드라이런</b>이다.
     *
     * <p>되돌릴 수 없는 유일한 배치이고, 보관 기간이 확정되기 전에 켜면 보관 의무 자료가 사라진다.
     * 첫 배포는 대상 목록만 로그로 남긴다.
     */
    private boolean purgeEnabled = false;

    /** 한 번의 파기 배치가 처리할 최대 건수 — 트랜잭션이 통째로 길어지는 것을 막는다 */
    private int purgeBatchSize = 100;

    /** 노출 원천 로그 보관 일수. <b>0이면 삭제하지 않는다</b> (§24-8 ⓔ 미확정) */
    private int impressionRetentionDays = 0;
}
