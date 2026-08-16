package showroomz.domain.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostSuspension;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.domain.post.type.PostDeleteReason;
import showroomz.domain.post.type.PostNotificationEvent;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.type.SuspensionResolution;
import showroomz.global.config.properties.PostProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시간이 흘러야 일어나는 상태 전이 — <b>기한 내 미신청 → 영구 삭제</b> (§24-5 세 번째 갈래).
 *
 * <p>앞의 두 갈래(승인·반려)는 운영자의 조작으로 일어나지만, 이 갈래는 아무도 아무것도 하지 않아서
 * 일어난다. 그래서 배치가 없으면 이 결말만 영원히 오지 않고, 중지된 게시물이 무기한 떠 있게 된다.
 *
 * <p>통지는 반드시 남긴다. 사람의 조작 없이 콘텐츠가 사라지는 유일한 경로라, 여기서 통지를 빠뜨리면
 * §24-5의 "알리지 않고 사라지는 경우는 없다"가 정확히 이 지점에서 깨진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostLifecycleService {

    private final PostSuspensionRepository postSuspensionRepository;
    private final PostNotificationService postNotificationService;
    private final PostProperties postProperties;

    /**
     * 기한이 지났는데 이의 신청이 없는 조치를 닫고 게시물을 삭제 상태로 넘긴다.
     *
     * @return 처리 건수
     */
    @Transactional
    public int expireOverdueSuspensions(LocalDateTime now, int limit) {
        List<PostSuspension> expired =
                postSuspensionRepository.findExpiredWithoutAppeal(now, Pageable.ofSize(limit));

        for (PostSuspension suspension : expired) {
            Post post = suspension.getPost();

            // 그 사이 본인이 삭제했거나 다른 경로로 처리됐다면 조치만 닫는다 — 상태를 두 번 덮지 않는다.
            if (post.getStatus() != PostStatus.SUSPENDED) {
                suspension.resolve(SuspensionResolution.DELETED_BY_SELF, now);
                continue;
            }

            suspension.resolve(SuspensionResolution.DELETED_BY_EXPIRE, now);
            post.softDelete(PostDeleteReason.APPEAL_EXPIRED, now,
                    now.plusMonths(postProperties.getRetentionMonths()));

            postNotificationService.notify(post, PostNotificationEvent.DELETED_BY_EXPIRE,
                    PostNotificationService.payload(
                            "suspensionId", suspension.getId(),
                            "reasonCode", suspension.getReasonCode().name(),
                            "reasonLabel", suspension.getReasonCode().getLabel(),
                            "policyRef", suspension.getPolicyRef(),
                            "appealDeadline", suspension.getAppealDeadline().toString(),
                            "deletedAt", now.toString(),
                            "retentionMonths", postProperties.getRetentionMonths()));
        }

        return expired.size();
    }
}
