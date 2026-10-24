package showroomz.domain.post.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import showroomz.domain.post.type.PostMonitoringSort;
import showroomz.domain.post.type.PostMonitoringTab;
import showroomz.domain.post.type.PostStatus;
import showroomz.domain.post.vo.PostMonitoringCounts;
import showroomz.domain.post.vo.PostMonitoringNeighbors;
import showroomz.domain.post.vo.PostMonitoringRow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 일반 게시물 모니터링 목록 · 탭 건수 (§54-2 · 54 설계 4-1 · 4-2).
 *
 * <p><b>네이티브 SQL인 이유</b> — 탭과 정렬이 <b>파생 상태</b>(대기 신고 수 · 열린 조치 기한 · 심사 대기 시각)에 걸린다.
 * 셋 다 자식 테이블 집계라 JPQL로 쓰면 ORDER BY 안에 상관 서브쿼리가 들어가고, 그 지원 범위가 Hibernate 버전에 달려 있다.
 * 파생 테이블로 한 번 감싸 계산 열을 만들고 바깥에서 그 열로 거르고 정렬한다 — MySQL과 테스트 H2(MySQL 모드)가 같은 문장을 받는다.
 *
 * <p>SQL에 사용자 입력을 이어 붙이지 않는다. 바뀌는 조각은 enum으로 고른 고정 문자열뿐이고 값은 전부 바인딩이다.
 *
 * <p>대상은 일반 게시물 · 작성중 제외다(§54-0 — 작성중은 노출된 적이 없다). 공구 게시물은 별도 화면 소관이다.
 */
@Repository
@RequiredArgsConstructor
public class PostMonitoringQueryRepository {

    /** 게시물별 집계 열 — 바깥 쿼리가 이 이름으로 거르고 정렬한다. 끝의 조건 자리에 필터를 덧붙인다 */
    private static final String BASE_SELECT = """
            SELECT p.post_id      AS post_id,
                   p.status       AS status,
                   p.published_at AS published_at,
                   p.deleted_at   AS deleted_at,
                   (SELECT COUNT(*) FROM post_report r
                     WHERE r.post_id = p.post_id AND r.status = 'PENDING') AS pending_cnt,
                   (SELECT COUNT(*) FROM post_report r
                     WHERE r.post_id = p.post_id AND r.status IN ('PENDING', 'ACCEPTED')) AS report_cnt,
                   (SELECT MIN(r.reported_at) FROM post_report r
                     WHERE r.post_id = p.post_id AND r.status = 'PENDING') AS first_pending_at,
                   (SELECT MAX(s.appeal_deadline) FROM post_suspension s
                     WHERE s.post_id = p.post_id AND s.resolution IS NULL) AS open_deadline,
                   (SELECT MAX(a.submitted_at) FROM post_appeal a
                     WHERE a.post_id = p.post_id AND a.status = 'PENDING') AS appeal_submitted_at
              FROM post p
              JOIN creator c ON c.creator_id = p.creator_id
              JOIN users u ON u.user_id = c.user_id
             WHERE p.post_type = 'GENERAL'
            """;

    private static final String EXCLUDE_DRAFT = " AND p.status <> 'DRAFT'";

    /** 조치 필요 우선 순위 — 0: 심사 중 · 신고 접수 / 1: 노출 중지 / 2: 게시중 / 3: 영구 삭제 */
    private static final String RANKED = """
            SELECT t.*,
                   CASE WHEN t.status = 'UNDER_REVIEW' THEN 0
                        WHEN t.status = 'PUBLISHED' AND t.pending_cnt > 0 THEN 0
                        WHEN t.status = 'SUSPENDED' THEN 1
                        WHEN t.status = 'PUBLISHED' THEN 2
                        ELSE 3 END AS rnk,
                   COALESCE(t.appeal_submitted_at, t.first_pending_at) AS waiting_since
              FROM (%s) t
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Page<PostMonitoringRow> findPage(PostMonitoringTab tab, PostMonitoringSort sort, String keyword,
                                            Long creatorId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String ranked = RANKED.formatted(BASE_SELECT + EXCLUDE_DRAFT + filters(keyword, creatorId, params));
        String where = tabCondition(tab == null ? PostMonitoringTab.ALL : tab);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + ranked + ") x WHERE " + where, params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        String sql = "SELECT * FROM (" + ranked + ") x WHERE " + where
                + " ORDER BY " + orderBy(sort == null ? PostMonitoringSort.ACTION_FIRST : sort)
                + " LIMIT :limit OFFSET :offset";
        List<PostMonitoringRow> rows = jdbc.query(sql, params, PostMonitoringQueryRepository::toRow);
        return new PageImpl<>(rows, pageable, total);
    }

    /** 탭 건수 — 탭 하나씩 부르면 요청이 다섯 배고 탭을 옮길 때마다 숫자가 흔들린다. 검색어와 무관한 전체 기준이다 */
    public PostMonitoringCounts countByTab() {
        String sql = """
                SELECT COUNT(*) AS all_cnt,
                       COALESCE(SUM(CASE WHEN t.status = 'PUBLISHED' AND t.pending_cnt > 0 THEN 1 ELSE 0 END), 0) AS reported_cnt,
                       COALESCE(SUM(CASE WHEN t.status = 'SUSPENDED' THEN 1 ELSE 0 END), 0) AS suspended_cnt,
                       COALESCE(SUM(CASE WHEN t.status = 'UNDER_REVIEW' THEN 1 ELSE 0 END), 0) AS review_cnt,
                       COALESCE(SUM(CASE WHEN t.status = 'DELETED' THEN 1 ELSE 0 END), 0) AS closed_cnt
                  FROM (%s) t
                """.formatted(BASE_SELECT + EXCLUDE_DRAFT);
        PostMonitoringCounts counts = jdbc.queryForObject(sql, new MapSqlParameterSource(),
                (rs, rowNum) -> new PostMonitoringCounts(rs.getLong("all_cnt"), rs.getLong("reported_cnt"),
                        rs.getLong("suspended_cnt"), rs.getLong("review_cnt"), rs.getLong("closed_cnt")));
        return counts == null ? new PostMonitoringCounts(0, 0, 0, 0, 0) : counts;
    }

    /**
     * 조치 필요(신고 접수 + 이의 신청 심사 중 = {@link PostMonitoringCounts#actionRequired()}) 중 가장 오래 기다린 시각 — 어드민 홈
     * 「신고 게시물 검토」 대기 행의 경과(56 E1). 「조치 필요 우선」 정렬의 순위 0 · 2차 키({@code waiting_since} = 대기 이의 신청 접수, 없으면
     * 가장 이른 대기 신고)와 같은 식이라 목록 맨 위 행의 대기 시작과 같다. 없으면 null.
     */
    public LocalDateTime findOldestActionRequiredSince() {
        String sql = "SELECT MIN(x.waiting_since) AS oldest FROM (" + RANKED.formatted(BASE_SELECT + EXCLUDE_DRAFT)
                + ") x WHERE x.rnk = 0";
        List<LocalDateTime> rows = jdbc.query(sql, new MapSqlParameterSource(),
                (rs, rowNum) -> rs.getObject("oldest", LocalDateTime.class));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 상세의 이전 · 다음(55 계획서 A7) — 목록은 탭 · 정렬 · 검색어마다 순서가 달라 id 순으로 셀 수 없다.
     * {@link #findPage}와 같은 순위 · 탭 조건 · 정렬로 줄을 세우고 {@code LAG} · {@code LEAD}로 앞뒤 id를 읽는다 —
     * 정렬 끝에 {@code post_id}가 있어 순서가 하나로 정해진다. 그 목록에 없는 게시물(탭 밖 · 작성중)이면 {@link PostMonitoringNeighbors#NONE}.
     * 창 함수는 MySQL 8 · H2가 같은 문장을 받는다.
     */
    public PostMonitoringNeighbors findNeighbors(Long postId, PostMonitoringTab tab, PostMonitoringSort sort,
                                                 String keyword, Long creatorId) {
        MapSqlParameterSource params = new MapSqlParameterSource("postId", postId);
        String ranked = RANKED.formatted(BASE_SELECT + EXCLUDE_DRAFT + filters(keyword, creatorId, params));
        String where = tabCondition(tab == null ? PostMonitoringTab.ALL : tab);
        String order = orderBy(sort == null ? PostMonitoringSort.ACTION_FIRST : sort);
        String sql = "SELECT n.prev_id, n.next_id FROM ("
                + "SELECT x.post_id, LAG(x.post_id) OVER (ORDER BY " + order + ") AS prev_id,"
                + " LEAD(x.post_id) OVER (ORDER BY " + order + ") AS next_id"
                + " FROM (" + ranked + ") x WHERE " + where
                + ") n WHERE n.post_id = :postId";
        List<PostMonitoringNeighbors> rows = jdbc.query(sql, params, (rs, rowNum) -> new PostMonitoringNeighbors(
                rs.getObject("prev_id", Long.class), rs.getObject("next_id", Long.class)));
        return rows.isEmpty() ? PostMonitoringNeighbors.NONE : rows.get(0);
    }

    /**
     * 상세 한 건의 집계 — 목록과 같은 정의를 써야 상세의 상태 배지가 목록과 어긋나지 않는다.
     * 작성중도 돌려준다(상세는 직접 열면 보인다). 공구 게시물이면 null.
     */
    public PostMonitoringRow findRow(Long postId) {
        MapSqlParameterSource params = new MapSqlParameterSource("postId", postId);
        String sql = RANKED.formatted(BASE_SELECT + " AND p.post_id = :postId");
        List<PostMonitoringRow> rows = jdbc.query(sql, params, PostMonitoringQueryRepository::toRow);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ------------------------------------------------------------------ 내부

    /** 쇼룸명(없으면 닉네임 — 화면 표기와 같은 대체 규칙) · 본문 OR 검색 */
    private static String filters(String keyword, Long creatorId, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder();
        if (creatorId != null) {
            sql.append(" AND p.creator_id = :creatorId");
            params.addValue("creatorId", creatorId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (c.showroom_name LIKE :kw OR (c.showroom_name IS NULL AND u.nickname LIKE :kw)")
                    .append(" OR p.content LIKE :kw)");
            params.addValue("kw", "%" + escapeLike(keyword.trim()) + "%");
        }
        return sql.toString();
    }

    private static String tabCondition(PostMonitoringTab tab) {
        return switch (tab) {
            case ALL -> "1 = 1";
            case REPORTED -> "x.status = 'PUBLISHED' AND x.pending_cnt > 0";
            case SUSPENDED -> "x.status = 'SUSPENDED'";
            case UNDER_REVIEW -> "x.status = 'UNDER_REVIEW'";
            case CLOSED -> "x.status = 'DELETED'";
        };
    }

    /**
     * 순위 안의 2차 키는 순위마다 다르다 — 해당하지 않는 순위에서는 NULL이 되어 영향이 없다.
     * 각 순위의 키는 그 순위 행에서 항상 값이 있다(심사 중 = 접수 시각 · 신고 접수 = 최초 대기 신고 · 중지 = 기한 · …).
     */
    private static String orderBy(PostMonitoringSort sort) {
        return switch (sort) {
            case ACTION_FIRST -> "x.rnk ASC,"
                    + " CASE WHEN x.rnk = 0 THEN x.waiting_since END ASC,"
                    + " CASE WHEN x.rnk = 1 THEN x.open_deadline END ASC,"
                    + " CASE WHEN x.rnk = 2 THEN x.published_at END DESC,"
                    + " CASE WHEN x.rnk = 3 THEN x.deleted_at END DESC,"
                    + " x.post_id DESC";
            case LATEST -> "x.published_at DESC, x.post_id DESC";
            case MOST_REPORTED -> "x.report_cnt DESC, x.published_at DESC, x.post_id DESC";
        };
    }

    /** MySQL · H2 모두 기본 이스케이프 문자가 역슬래시다 */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static PostMonitoringRow toRow(ResultSet rs, int rowNum) throws SQLException {
        return new PostMonitoringRow(
                rs.getLong("post_id"),
                PostStatus.valueOf(rs.getString("status")),
                rs.getLong("pending_cnt"),
                rs.getLong("report_cnt"),
                rs.getObject("first_pending_at", LocalDateTime.class),
                rs.getObject("open_deadline", LocalDateTime.class),
                rs.getObject("appeal_submitted_at", LocalDateTime.class));
    }
}
