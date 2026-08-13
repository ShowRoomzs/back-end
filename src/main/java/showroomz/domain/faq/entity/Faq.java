package showroomz.domain.faq.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.faq.type.FaqCategory;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "FAQ",
        indexes = {
                // 노출 순서는 카테고리 안에서만 유효하다 (기획 §19-4)
                @Index(name = "idx_faq_category_display_order", columnList = "CATEGORY, DISPLAY_ORDER")
        }
)
public class Faq extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FAQ_ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 30)
    private FaqCategory category;

    // 3. 질문 내용
    @Column(name = "QUESTION", nullable = false, columnDefinition = "TEXT")
    private String question;

    // 4. 답변 내용
    @Column(name = "ANSWER", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder;

    @Builder
    public Faq(FaqCategory category, String question, String answer, Integer displayOrder) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
    }

    public void update(FaqCategory category, String question, String answer) {
        this.category = category;
        this.question = question;
        this.answer = answer;
    }

    public void updateDisplayOrder(Integer newOrder) {
        this.displayOrder = newOrder;
    }
}
