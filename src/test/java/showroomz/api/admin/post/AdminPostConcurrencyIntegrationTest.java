package showroomz.api.admin.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.admin.post.dto.AdminPostDto;
import showroomz.api.admin.post.service.AdminPostService;
import showroomz.domain.post.type.PostPolicyClause;
import showroomz.domain.post.type.PostSuspensionReason;
import showroomz.global.error.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [통합] 어드민 일반 게시물 — 동시 조치 (54 테스트 시나리오 20-2 · 설계 0-8 · 12-2).
 *
 * <p>조치는 게시물(또는 이의 신청) 행을 잠근 뒤 상태를 판정한다. 잠금이 없으면 두 운영자가 같은 게시물에 열린 조치를 두 행
 * 만들고 기한 만료 배치가 둘을 집는다. 서비스를 두 스레드에서 같은 순간에 출발시켜 한쪽만 이기는지 본다.
 */
@DisplayName("[통합] 어드민 일반 게시물 — 동시 조치")
class AdminPostConcurrencyIntegrationTest extends AdminPostScenarioSupport {

    private static final AdminPostDto.SuspendRequest SUSPEND =
            new AdminPostDto.SuspendRequest(PostSuspensionReason.MISLEADING_INFO, null, PostPolicyClause.ARTICLE_3_2);

    @Autowired
    private AdminPostService adminPostService;

    /** 첫 요청의 초기화 비용이 잠금 대기 시간을 넘기지 않도록 경로를 한 번 데운다 */
    @BeforeEach
    void warmUp() throws Exception {
        Long postId = publish(harin, "예열", LocalDateTime.now().minusDays(1));
        report(postId, "warm", "가나다", LocalDateTime.now().minusHours(1));
        adminPostService.closeReports(operator.getId(), postId);
        adminPostService.suspend(operator.getId(), postId, SUSPEND);
        adminPostService.cancelSuspension(operator.getId(), postId, null);
    }

    @Test
    @DisplayName("[CN-01] 같은 게시물 동시 노출 중지 2회 — 하나만 성공하고 열린 조치 · 통지는 1건이다")
    void concurrentSuspend() throws Exception {
        Long postId = publish(soyeon, "동시 중지", LocalDateTime.now().minusDays(1));

        List<String> results = race(
                () -> adminPostService.suspend(operator.getId(), postId, SUSPEND).getStatus().name(),
                () -> adminPostService.suspend(operator2.getId(), postId, SUSPEND).getStatus().name());

        assertThat(results).containsExactlyInAnyOrder("SUSPENDED", "POST_NOT_EDITABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_suspension WHERE post_id = ? AND resolution IS NULL",
                Integer.class, postId)).isEqualTo(1);
        assertThat(notificationCount(postId, "SUSPENDED")).isEqualTo(1);
    }

    @Test
    @DisplayName("[CN-02] 노출 중지와 신고 종결이 동시에 — 어느 쪽이 이기든 신고는 한 상태로만 닫힌다")
    void suspendVersusClose() throws Exception {
        Long postId = publish(soyeon, "중지 대 종결", LocalDateTime.now().minusDays(1));
        report(postId, "cn2-1", "한지준", LocalDateTime.now().minusHours(2));
        report(postId, "cn2-2", "오서린", LocalDateTime.now().minusHours(1));

        List<String> results = race(
                () -> "suspend:" + adminPostService.suspend(operator.getId(), postId, SUSPEND).getClosedReportCount(),
                () -> "close:" + adminPostService.closeReports(operator2.getId(), postId).getClosedReportCount());

        List<String> statuses = reportStatuses(postId);
        if (results.contains("suspend:2")) {
            // 중지가 먼저 잠갔다 — 신고는 조치로 이어졌고 종결할 것이 남지 않는다
            assertThat(results).containsExactly("suspend:2", "POST_REPORT_NOTHING_TO_CLOSE");
            assertThat(statuses).containsOnly("ACCEPTED");
        } else {
            // 종결이 먼저 잠갔다 — 신고는 반려됐고 중지는 닫을 신고 없이 성공한다
            assertThat(results).containsExactly("suspend:0", "close:2");
            assertThat(statuses).containsOnly("DISMISSED");
        }
        assertThat(row("SELECT status FROM post WHERE post_id = ?", postId)).containsEntry("status", "SUSPENDED");
    }

    @Test
    @DisplayName("[CN-03] 같은 조치를 동시에 취소 2회 — 하나만 성공하고 취소 통지는 1건이다")
    void concurrentCancel() throws Exception {
        Long postId = publish(soyeon, "동시 취소", LocalDateTime.now().minusDays(1));
        suspend(postId);

        List<String> results = race(
                () -> adminPostService.cancelSuspension(operator.getId(), postId, null).getStatus().name(),
                () -> adminPostService.cancelSuspension(operator2.getId(), postId, null).getStatus().name());

        assertThat(results).containsExactlyInAnyOrder("PUBLISHED", "POST_SUSPENSION_NOT_CANCELABLE");
        assertThat(notificationCount(postId, "SUSPENSION_CANCELED")).isEqualTo(1);
    }

    @Test
    @DisplayName("[CN-04] 같은 이의 신청에 승인과 반려가 동시에 — 하나만 성공하고 게시물 상태가 이긴 쪽과 맞는다")
    void approveVersusReject() throws Exception {
        Long postId = publish(soyeon, "승인 대 반려", LocalDateTime.now().minusDays(1));
        suspend(postId);
        Long appealId = appeal(postId);

        List<String> results = race(
                () -> "approve:" + adminPostService.approveAppeal(operator.getId(), appealId,
                        new AdminPostDto.AppealReviewRequest(null)).getStatus(),
                () -> "reject:" + adminPostService.rejectAppeal(operator2.getId(), appealId,
                        new AdminPostDto.AppealReviewRequest("재게시할 수 없습니다.")).getStatus());

        assertThat(results).filteredOn("POST_APPEAL_ALREADY_REVIEWED"::equals).hasSize(1);
        String postStatus = (String) row("SELECT status FROM post WHERE post_id = ?", postId).get("status");
        String appealStatus = (String) row("SELECT status FROM post_appeal WHERE appeal_id = ?", appealId).get("status");
        if (results.contains("approve:PUBLISHED")) {
            assertThat(postStatus).isEqualTo("PUBLISHED");
            assertThat(appealStatus).isEqualTo("APPROVED");
            assertThat(notificationCount(postId, "APPEAL_REJECTED")).isZero();
        } else {
            assertThat(results).contains("reject:DELETED");
            assertThat(postStatus).isEqualTo("DELETED");
            assertThat(appealStatus).isEqualTo("REJECTED");
            assertThat(notificationCount(postId, "APPEAL_APPROVED")).isZero();
        }
    }

    /** 호출들을 같은 순간에 출발시키고 결과를 호출 순서대로 모은다 — 업무 예외는 에러 코드 이름으로 바꾼다 */
    @SafeVarargs
    private static List<String> race(Callable<String>... calls) throws Exception {
        CountDownLatch ready = new CountDownLatch(calls.length);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.length)) {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return call.call();
                    } catch (BusinessException e) {
                        return e.getErrorCode().name();
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
