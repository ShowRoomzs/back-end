package showroomz.api.admin.post.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.post.entity.Post;
import showroomz.global.utils.MaskingUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 어드민 일반 게시물 화면의 표기 규칙 (54 설계 10절 「유틸」) */
class AdminPostFormatsTest {

    @Test
    @DisplayName("신고자 이름은 가운데를 가린다 — 한*준 · 박**이 · 한*")
    void masksNameInTheMiddle() {
        assertThat(MaskingUtils.maskNameMiddle("한지준")).isEqualTo("한*준");
        assertThat(MaskingUtils.maskNameMiddle("박다은이")).isEqualTo("박**이");
        assertThat(MaskingUtils.maskNameMiddle("한준")).isEqualTo("한*");
        assertThat(MaskingUtils.maskNameMiddle("한")).isEqualTo("*");
        assertThat(MaskingUtils.maskNameMiddle("  ")).isNull();
        assertThat(MaskingUtils.maskNameMiddle(null)).isNull();
    }

    @Test
    @DisplayName("게시물 비율 표기 — 표준 비율은 이름으로, 나머지는 N:1")
    void aspectRatioLabel() {
        assertThat(AdminPostFormats.aspectRatioLabel(new BigDecimal("0.8000"))).isEqualTo("4:5");
        assertThat(AdminPostFormats.aspectRatioLabel(new BigDecimal("1.0000"))).isEqualTo("1:1");
        assertThat(AdminPostFormats.aspectRatioLabel(new BigDecimal("1.5000"))).isEqualTo("3:2");
        assertThat(AdminPostFormats.aspectRatioLabel(new BigDecimal("1.9100"))).isEqualTo("1.91:1");
        assertThat(AdminPostFormats.aspectRatioLabel(new BigDecimal("1.4000"))).isEqualTo("1.4:1");
        assertThat(AdminPostFormats.aspectRatioLabel(null)).isNull();
    }

    @Test
    @DisplayName("원본 뷰어 파일명은 원본 URL의 마지막 조각이다 — 쿼리 문자열은 뗀다")
    void fileName() {
        assertThat(AdminPostFormats.fileName("https://cdn.test/post/IMG_3302.jpg?v=2")).isEqualTo("IMG_3302.jpg");
        assertThat(AdminPostFormats.fileName("IMG_1.png")).isEqualTo("IMG_1.png");
        assertThat(AdminPostFormats.fileName(null)).isNull();
    }

    @Test
    @DisplayName("게시물 번호는 게시일 + id 4자리다")
    void postNo() {
        Creator creator = Creator.builder().id(1L).showroomName("소연").build();
        Post post = Post.published(creator, "본문", new BigDecimal("0.8000"), LocalDateTime.of(2026, 8, 11, 21, 5));
        ReflectionTestUtils.setField(post, "id", 342L);

        assertThat(AdminPostFormats.postNo(post)).isEqualTo("POST-20260811-0342");
    }

    @Test
    @DisplayName("본문 미리보기는 공백을 접고 80자에서 자른다 · 본문이 없으면 null")
    void preview() {
        assertThat(AdminPostFormats.preview("가  나\n다")).isEqualTo("가 나 다");
        assertThat(AdminPostFormats.preview("가".repeat(81))).isEqualTo("가".repeat(80) + "…");
        assertThat(AdminPostFormats.preview("   ")).isNull();
    }
}
