package showroomz.api.admin.post.service;

import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 어드민 일반 게시물 화면의 표기 규칙 — 목록 · 상세 · 이의 신청 목록이 같은 문자열을 내도록 한 곳에 둔다.
 */
final class AdminPostFormats {

    /** 목록 한 줄 미리보기 — 시안 C1 게시물 열 폭 */
    static final int CONTENT_PREVIEW_LENGTH = 80;

    private static final DateTimeFormatter POST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 표준 비율 표기 — 허용 범위(4:5 ~ 1.91:1) 안의 흔한 값. 가까운 것이 없으면 「1.33:1」처럼 적는다 */
    private static final List<Object[]> KNOWN_RATIOS = List.of(
            new Object[]{new BigDecimal("0.8000"), "4:5"},
            new Object[]{new BigDecimal("1.0000"), "1:1"},
            new Object[]{new BigDecimal("1.2500"), "5:4"},
            new Object[]{new BigDecimal("1.3333"), "4:3"},
            new Object[]{new BigDecimal("1.5000"), "3:2"},
            new Object[]{new BigDecimal("1.7778"), "16:9"},
            new Object[]{new BigDecimal("1.9100"), "1.91:1"});
    private static final BigDecimal RATIO_TOLERANCE = new BigDecimal("0.0050");

    private AdminPostFormats() {
    }

    /** 쇼룸명 — 없으면 닉네임(§22-1 대체 규칙) */
    static String showroomName(Creator creator) {
        if (creator == null) {
            return null;
        }
        if (creator.getShowroomName() != null) {
            return creator.getShowroomName();
        }
        return creator.getUser() == null ? null : creator.getUser().getNickname();
    }

    static String preview(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String flattened = content.replaceAll("\\s+", " ").trim();
        return flattened.length() <= CONTENT_PREVIEW_LENGTH
                ? flattened
                : flattened.substring(0, CONTENT_PREVIEW_LENGTH) + "…";
    }

    /**
     * 게시물 번호 — 잠정 포맷 {@code POST-{게시일 yyyyMMdd}-{id 4자리 0패딩}}(54 설계 11-2 #2).
     * 게시된 적 없는 게시물은 생성일을 쓴다. 4자리를 넘는 id는 그대로 늘어난다.
     */
    static String postNo(Post post) {
        LocalDateTime base = post.getPublishedAt() != null ? post.getPublishedAt() : post.getCreatedAt();
        String date = base == null ? "00000000" : base.format(POST_NO_DATE);
        return "POST-" + date + "-" + String.format("%04d", post.getId());
    }

    /** 게시물 비율 표기 — 0.8 → 4:5 · 1.5 → 3:2 · 1.91 → 1.91:1 */
    static String aspectRatioLabel(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        for (Object[] known : KNOWN_RATIOS) {
            BigDecimal value = (BigDecimal) known[0];
            if (ratio.subtract(value).abs().compareTo(RATIO_TOLERANCE) <= 0) {
                return (String) known[1];
            }
        }
        return ratio.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + ":1";
    }

    /** 원본 URL의 마지막 경로 조각 — 업로드 원 파일명은 저장하지 않는다(54 설계 11-2 #3) */
    static String fileName(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
