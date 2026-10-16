package showroomz.domain.post.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.post.entity.PostOriginalDownload;

import java.util.List;

/** 원본 내려받기 기록 — 파기 배치도 지우지 않는다(삭제 메서드를 두지 않는다) */
public interface PostOriginalDownloadRepository extends JpaRepository<PostOriginalDownload, Long> {

    List<PostOriginalDownload> findByPostIdOrderByDownloadedAtAsc(Long postId);
}
