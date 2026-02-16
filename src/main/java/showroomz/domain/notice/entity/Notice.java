package showroomz.domain.notice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "NOTICE")
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTICE_ID")
    private Long id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "IS_VISIBLE")
    private boolean isVisible;

    @Builder
    public Notice(String title, String content, boolean isVisible) {
        this.title = title;
        this.content = content;
        this.isVisible = isVisible;
    }

    public void update(String title, String content, boolean isVisible) {
        this.title = title;
        this.content = content;
        this.isVisible = isVisible;
    }
}
