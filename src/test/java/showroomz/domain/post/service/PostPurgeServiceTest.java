package showroomz.domain.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;
import showroomz.domain.post.entity.PostImage;
import showroomz.domain.post.repository.PostAppealRepository;
import showroomz.domain.post.repository.PostImageRepository;
import showroomz.domain.post.repository.PostImpressionRepository;
import showroomz.domain.post.repository.PostLikeRepository;
import showroomz.domain.post.repository.PostRepository;
import showroomz.domain.post.repository.PostSuspensionRepository;
import showroomz.global.config.properties.PostProperties;
import showroomz.global.config.properties.S3Properties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 파기 (§24-6) — 이 프로젝트에서 <b>되돌릴 수 없는 유일한 배치</b>다.
 *
 * <p>그래서 이 테스트가 지키는 첫 번째 계약은 "기본값은 드라이런"이다. 보관 기간이 확정되기 전에
 * 켜지면 보관 의무가 있는 자료가 사라지고 복구 경로가 없다 — 설정 기본값이 바뀌는 사고를 여기서 잡는다.
 *
 * <p>두 번째는 <b>삭제 순서</b>다. 자식부터(FK 역순) 지워야 하고, {@code post_appeal}이
 * {@code post_suspension}을 참조하므로 이의 신청이 조치보다 먼저다. 순서를 어기면 제약에 걸려
 * 배치가 통째로 멈추고, 그러면 보관 기간이 지난 자료가 계속 남는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostPurgeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 4, 20);
    private static final long POST_ID = 42L;
    private static final String BUCKET = "showroomz-test";

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostImpressionRepository postImpressionRepository;
    @Mock
    private PostAppealRepository postAppealRepository;
    @Mock
    private PostSuspensionRepository postSuspensionRepository;
    @Mock
    private S3Client s3Client;

    /** 프로퍼티는 값 객체다 — 모킹하면 기본값이 사라져 "기본이 드라이런"을 검증할 수 없다. */
    @Spy
    private PostProperties postProperties = new PostProperties();
    @Spy
    private S3Properties s3Properties = new S3Properties();

    @InjectMocks
    private PostPurgeService postPurgeService;

    @BeforeEach
    void setUpProperties() {
        s3Properties.setBucket(BUCKET);
    }

    private Post post(long id) {
        Creator showroom = Creator.builder().id(5L).showroomName("소연 뷰티").build();
        Post post = Post.published(showroom, "본문", new BigDecimal("0.8000"), NOW.minusMonths(7));
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private void givenTargets(Post... posts) {
        given(postRepository.findPurgeTargets(any(), any())).willReturn(List.of(posts));
    }

    private void givenImages(long postId, PostImage... images) {
        given(postImageRepository.findByPost_IdOrderBySortOrderAsc(postId)).willReturn(List.of(images));
    }

    private PostImage image(String imageUrl, String originalUrl) {
        return new PostImage(imageUrl, originalUrl, 1080, 1080, 2048);
    }

    @Nested
    @DisplayName("드라이런 (기본값)")
    class DryRun {

        /**
         * 이 테스트가 실패하면 설정 기본값이 뒤집혔다는 뜻이다 — 배포 즉시 실제 삭제가 돈다.
         */
        @Test
        @DisplayName("기본 설정에서는 아무것도 지우지 않는다")
        void defaultConfigurationDeletesNothing() {
            assertThat(postProperties.isPurgeEnabled()).isFalse();
            givenTargets(post(POST_ID));

            int purged = postPurgeService.purgeExpired(NOW);

            assertThat(purged).isZero();
            verify(postRepository, never()).delete(any());
            verify(postImageRepository, never()).deleteAllByPostId(anyLong());
            verify(postLikeRepository, never()).deleteAllByPostId(anyLong());
            verify(postImpressionRepository, never()).deleteAllByPostId(anyLong());
            verify(postAppealRepository, never()).deleteAllByPostId(anyLong());
            verify(postSuspensionRepository, never()).deleteAllByPostId(anyLong());
        }

        /** 드라이런은 대상을 로그로 남기는 것이 목적이라 S3 객체도 건드리지 않아야 한다. */
        @Test
        @DisplayName("드라이런은 S3 객체도 지우지 않는다")
        void dryRunLeavesS3Untouched() {
            givenTargets(post(POST_ID));
            givenImages(POST_ID, image("https://cdn.test/a.jpg", "https://cdn.test/a-orig.jpg"));

            postPurgeService.purgeExpired(NOW);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("대상이 없으면 조회만 하고 끝낸다")
        void noTargetsShortCircuits() {
            given(postRepository.findPurgeTargets(any(), any())).willReturn(List.of());

            assertThat(postPurgeService.purgeExpired(NOW)).isZero();
            verify(postImageRepository, never()).findByPost_IdOrderBySortOrderAsc(anyLong());
        }
    }

    @Nested
    @DisplayName("실제 파기 (purge-enabled=true)")
    class Enabled {

        @BeforeEach
        void enablePurge() {
            postProperties.setPurgeEnabled(true);
        }

        /**
         * 순서가 이 테스트의 전부다. FK 역순이 아니면 제약 위반으로 배치가 멈춘다.
         * 특히 post_appeal → post_suspension 순서는 둘 사이의 참조 때문에 바꿀 수 없다.
         */
        @Test
        @DisplayName("자식부터 FK 역순으로 지우고 이의 신청이 조치보다 먼저다")
        void deletesChildrenInForeignKeyOrder() {
            Post target = post(POST_ID);
            givenTargets(target);
            givenImages(POST_ID);

            postPurgeService.purgeExpired(NOW);

            InOrder order = inOrder(postImageRepository, postLikeRepository, postImpressionRepository,
                    postAppealRepository, postSuspensionRepository, postRepository);
            order.verify(postImageRepository).deleteAllByPostId(POST_ID);
            order.verify(postLikeRepository).deleteAllByPostId(POST_ID);
            order.verify(postImpressionRepository).deleteAllByPostId(POST_ID);
            order.verify(postAppealRepository).deleteAllByPostId(POST_ID);
            order.verify(postSuspensionRepository).deleteAllByPostId(POST_ID);
            order.verify(postRepository).delete(target);
        }

        @Test
        @DisplayName("파기한 건수를 돌려준다")
        void returnsPurgedCount() {
            givenTargets(post(1L), post(2L), post(3L));
            givenImages(1L);
            givenImages(2L);
            givenImages(3L);

            assertThat(postPurgeService.purgeExpired(NOW)).isEqualTo(3);
            verify(postRepository, org.mockito.Mockito.times(3)).delete(any());
        }

        /** 원본 내려받기(§24-6 유예 기간)를 위해 남겨 둔 원본까지 이때 함께 정리된다. */
        @Test
        @DisplayName("가공본과 원본 객체를 모두 지운다")
        void deletesBothRenditionAndOriginal() {
            givenTargets(post(POST_ID));
            givenImages(POST_ID, image("https://cdn.test/posts/a.jpg", "https://cdn.test/posts/a-orig.jpg"));

            postPurgeService.purgeExpired(NOW);

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client, org.mockito.Mockito.times(2)).deleteObject(captor.capture());
            assertThat(captor.getAllValues()).extracting(DeleteObjectRequest::key)
                    .containsExactly("posts/a.jpg", "posts/a-orig.jpg");
            assertThat(captor.getAllValues()).extracting(DeleteObjectRequest::bucket)
                    .containsOnly(BUCKET);
        }

        /** 업로드가 키를 URL 인코딩해 붙이므로 역산할 때 되돌려야 실제 객체를 가리킨다. */
        @Test
        @DisplayName("URL 인코딩된 경로는 디코딩해 키로 되돌린다")
        void decodesEncodedKey() {
            givenTargets(post(POST_ID));
            givenImages(POST_ID, image("https://cdn.test/posts/%EC%86%8C%EC%97%B0%20a.jpg", null));

            postPurgeService.purgeExpired(NOW);

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertThat(captor.getValue().key()).isEqualTo("posts/소연 a.jpg");
        }

        /**
         * 객체 삭제 실패로 배치가 멈추면 보관 기간이 지난 자료가 계속 남는다 —
         * DB 행은 지우고 객체만 남기는 쪽이 낫다는 판단이 코드에 있고, 여기서 그것을 지킨다.
         */
        @Test
        @DisplayName("S3 삭제가 실패해도 DB 행 삭제는 계속된다")
        void s3FailureDoesNotStopDeletion() {
            givenTargets(post(POST_ID));
            givenImages(POST_ID, image("https://cdn.test/posts/a.jpg", null));
            willThrow(S3Exception.builder().message("AccessDenied").build())
                    .given(s3Client).deleteObject(any(DeleteObjectRequest.class));

            assertThat(postPurgeService.purgeExpired(NOW)).isEqualTo(1);
            verify(postRepository).delete(any());
        }

        @Test
        @DisplayName("한 건이 실패해도 나머지 게시물 파기는 이어진다")
        void oneFailureDoesNotAbortBatch() {
            givenTargets(post(1L), post(2L));
            givenImages(1L, image("https://cdn.test/posts/a.jpg", null));
            givenImages(2L, image("https://cdn.test/posts/b.jpg", null));
            willThrow(S3Exception.builder().message("AccessDenied").build())
                    .given(s3Client).deleteObject(any(DeleteObjectRequest.class));

            assertThat(postPurgeService.purgeExpired(NOW)).isEqualTo(2);
            verify(postRepository, org.mockito.Mockito.times(2)).delete(any());
        }

        @Test
        @DisplayName("이미지 URL이 비어 있으면 건너뛰고 행 삭제만 한다")
        void blankUrlIsSkipped() {
            givenTargets(post(POST_ID));
            givenImages(POST_ID, image("   ", null));

            postPurgeService.purgeExpired(NOW);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
            verify(postRepository).delete(any());
        }

        @Test
        @DisplayName("이미지가 없는 게시물도 정상적으로 파기된다")
        void postWithoutImagesIsPurged() {
            givenTargets(post(POST_ID));
            givenImages(POST_ID);

            assertThat(postPurgeService.purgeExpired(NOW)).isEqualTo(1);
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        /** 삭제 이력은 영구 보존이라 그 테이블에만 FK가 없다 — 파기가 건드리면 안 된다. */
        @Test
        @DisplayName("배치 크기 설정이 조회 한도로 전달된다")
        void batchSizeIsPassedToQuery() {
            postProperties.setPurgeBatchSize(7);
            given(postRepository.findPurgeTargets(any(), any())).willReturn(List.of());

            postPurgeService.purgeExpired(NOW);

            ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                    ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
            verify(postRepository).findPurgeTargets(any(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(7);
        }
    }
}
