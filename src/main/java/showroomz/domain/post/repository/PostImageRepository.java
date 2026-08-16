package showroomz.domain.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.post.entity.PostImage;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPost_IdOrderBySortOrderAsc(Long postId);

    /**
     * 목록 화면의 대표 사진 — 게시물마다 첫 장만 모아 한 번에 가져온다.
     * 목록에서 게시물마다 사진 전체를 끌고 오면 20장 × 페이지 크기만큼 헛일이 된다.
     */
    @Query("SELECT i FROM PostImage i WHERE i.post.id IN :postIds AND i.sortOrder = 0")
    List<PostImage> findRepresentativesByPostIds(@Param("postIds") List<Long> postIds);

    /**
     * 피드 한 페이지의 사진을 한 번에 가져온다.
     *
     * <p>소비자 피드는 게시물마다 사진 전체를 보여주므로(가로 스와이프) 게시물별로 지연 로딩하면
     * 페이지 크기만큼 쿼리가 늘어난다.
     */
    @Query("SELECT i FROM PostImage i WHERE i.post.id IN :postIds ORDER BY i.post.id ASC, i.sortOrder ASC")
    List<PostImage> findByPostIdsOrdered(@Param("postIds") List<Long> postIds);

    /** 목록의 "n장" 표기용 — 사진 본문을 읽지 않고 개수만 센다 */
    @Query("SELECT i.post.id, COUNT(i) FROM PostImage i WHERE i.post.id IN :postIds GROUP BY i.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    /** 파기 배치 — S3 객체를 지운 뒤 행을 정리한다 (§24-6) */
    @Modifying
    @Query("DELETE FROM PostImage i WHERE i.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);
}
