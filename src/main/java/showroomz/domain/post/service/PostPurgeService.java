package showroomz.domain.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.repository.PostAppealRepository;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostReportRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.config.properties.S3Properties;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 파기 — 보관 기간이 끝난 게시물을 <b>실제로</b> 지운다 (§24-6).
 *
 * <p>이 프로젝트에서 <b>되돌릴 수 없는 유일한 배치</b>다. 그래서 기본값이 드라이런이다
 * ({@code post.purge-enabled=false}). 보관 기간이 확정되기 전에 켜면 보관 의무가 있는 자료가 사라지고,
 * 그때는 되돌릴 방법이 없다. 첫 배포는 대상 목록만 로그로 남기고 실제 삭제는 다음 릴리스에서 켠다.
 *
 * <p>S3 객체도 이때 함께 지운다. 소프트 삭제 시점에 지우면 <b>원본 내려받기</b>(§24-6 유예 기간)와
 * 수사·행정기관 자료 요청 대응이 동시에 깨진다 — 화면에서 사라지는 것과 보관이 끝나는 것은 다른 사건이다.
 *
 * <p>{@code post_notification_log}는 건드리지 않는다. 삭제 이력은 영구 보존이고, 그래서 그 테이블에만
 * FK가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostPurgeService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostImpressionRepository postImpressionRepository;
    private final PostAppealRepository postAppealRepository;
    private final PostReportRepository postReportRepository;
    private final PostSuspensionRepository postSuspensionRepository;
    private final PostProperties postProperties;
    private final S3Properties s3Properties;
    private final S3Client s3Client;

    /**
     * @return 실제로 파기한 건수. 드라이런이면 0이고 대상만 로그로 남는다
     */
    @Transactional
    public int purgeExpired(LocalDateTime now) {
        List<Post> targets = postRepository.findPurgeTargets(
                now, Pageable.ofSize(postProperties.getPurgeBatchSize()));
        if (targets.isEmpty()) {
            return 0;
        }

        if (!postProperties.isPurgeEnabled()) {
            log.info("게시물 파기 드라이런 - 대상 {}건, postIds={}", targets.size(),
                    targets.stream().map(Post::getId).toList());
            return 0;
        }

        for (Post post : targets) {
            purgeOne(post);
        }
        log.info("게시물 파기 완료 - {}건", targets.size());
        return targets.size();
    }

    /**
     * 자식부터 지운다 — FK 역순이다. 순서를 어기면 제약에 걸려 배치가 통째로 멈춘다.
     *
     * <p>{@code post_appeal}이 {@code post_suspension}을 참조하므로 이의 신청이 조치보다 먼저다.
     */
    private void purgeOne(Post post) {
        Long postId = post.getId();

        for (PostImage image : postImageRepository.findByPost_IdOrderBySortOrderAsc(postId)) {
            deleteObjectSafely(image.getImageUrl());
            deleteObjectSafely(image.getOriginalUrl());
        }

        postImageRepository.deleteAllByPostId(postId);
        postLikeRepository.deleteAllByPostId(postId);
        postImpressionRepository.deleteAllByPostId(postId);
        postReportRepository.deleteAllByPostId(postId);
        postAppealRepository.deleteAllByPostId(postId);
        postSuspensionRepository.deleteAllByPostId(postId);
        postRepository.delete(post);
    }

    /**
     * S3 객체 삭제 실패가 배치를 멈추게 하지 않는다 — DB 행은 지우고 객체만 남는 쪽이,
     * 파기가 통째로 멈춰 보관 기간이 지난 자료가 계속 남는 것보다 낫다. 실패는 로그로 추적한다.
     */
    private void deleteObjectSafely(String url) {
        String key = toS3Key(url);
        if (key == null) {
            log.warn("파기 대상 이미지의 S3 키를 알 수 없어 건너뛴다 - url={}", url);
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("게시물 이미지 객체 삭제 실패 - key={}", key, e);
        }
    }

    /**
     * 업로드가 URL만 저장하기 때문에 키를 역산한다.
     *
     * <p>업로드 쪽이 키를 URL 인코딩해 붙였으므로 여기서 되돌린다. CloudFront를 쓰든 S3 기본 도메인을
     * 쓰든 경로 부분은 같다.
     */
    private static String toS3Key(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            return URLDecoder.decode(path.startsWith("/") ? path.substring(1) : path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
