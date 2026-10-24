package showroomz.domain.post.vo;

/**
 * 어드민 게시물 상세의 이전 · 다음(55 계획서 A7) — 상세를 연 목록(탭 · 정렬 · 검색어)의 순서에서 바로 앞 · 뒤 게시물.
 * 첫 건 · 끝 건이면 그쪽이 null, 그 목록에 없는 게시물이면 둘 다 null이다.
 */
public record PostMonitoringNeighbors(Long previousPostId, Long nextPostId) {

    public static final PostMonitoringNeighbors NONE = new PostMonitoringNeighbors(null, null);
}
