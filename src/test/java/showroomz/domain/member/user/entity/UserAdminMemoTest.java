package showroomz.domain.member.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소비자 관리자 메모(§25-6) — 「오타 정정은 열어 두고 사후 편집은 막는다」: 작성자 본인 · 작성 후 24시간. 옛 단일 메모 이관 행(작성자 없음)은
 * 누구도 고치지 못한다(V204).
 */
class UserAdminMemoTest {

    private static final long AUTHOR = 7L;
    private static final long OTHER = 8L;
    private static final LocalDateTime WRITTEN = LocalDateTime.of(2026, 10, 10, 9, 0);

    @Test
    @DisplayName("작성자 본인은 24시간 경계까지 고칠 수 있고, 1초라도 넘으면 못 고친다")
    void editWindowBoundary() {
        UserAdminMemo memo = UserAdminMemo.write(1L, AUTHOR, "김운영", "반품 사유 반복", WRITTEN);

        assertThat(memo.isEditableBy(AUTHOR, WRITTEN.plusHours(24))).isTrue();
        assertThat(memo.isEditableBy(AUTHOR, WRITTEN.plusHours(24).plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("작성자가 아니면 시간과 관계없이 못 고친다")
    void otherOperatorCannotEdit() {
        UserAdminMemo memo = UserAdminMemo.write(1L, AUTHOR, "김운영", "반품 사유 반복", WRITTEN);

        assertThat(memo.isAuthor(OTHER)).isFalse();
        assertThat(memo.isEditableBy(OTHER, WRITTEN.plusMinutes(1))).isFalse();
    }

    @Test
    @DisplayName("이관 메모(작성자 없음)는 누구의 메모도 아니다")
    void migratedMemoHasNoAuthor() {
        UserAdminMemo memo = UserAdminMemo.write(1L, null, "이전 단일 메모(작성자 미기록)", "블랙리스트 의심", WRITTEN);

        assertThat(memo.isAuthor(AUTHOR)).isFalse();
        assertThat(memo.isAuthor(null)).isFalse();
        assertThat(memo.isEditableBy(AUTHOR, WRITTEN)).isFalse();
    }

    @Test
    @DisplayName("수정은 「수정됨」 시각을, 삭제는 soft delete 시각을 남긴다")
    void editAndDeleteLeaveTimestamps() {
        UserAdminMemo memo = UserAdminMemo.write(1L, AUTHOR, "김운영", "오타있음", WRITTEN);

        memo.edit("오타 정정", WRITTEN.plusMinutes(3));
        memo.delete(WRITTEN.plusMinutes(5));

        assertThat(memo.getContent()).isEqualTo("오타 정정");
        assertThat(memo.getModifiedAt()).isEqualTo(WRITTEN.plusMinutes(3));
        assertThat(memo.getDeletedAt()).isEqualTo(WRITTEN.plusMinutes(5));
    }
}
