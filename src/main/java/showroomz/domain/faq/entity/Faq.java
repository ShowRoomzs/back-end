package showroomz.domain.faq.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.inquiry.type.InquiryType;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "FAQ")
public class Faq extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FAQ_ID")
    private Long id;

    // 1. 질문 타입 (대분류 - InquiryType 재사용)
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private InquiryType type;

    // 2. 카테고리 (소분류 - 직접 입력)
    @Column(name = "CATEGORY", nullable = false, length = 50)
    private String category;

    // 3. 질문 내용
    @Column(name = "QUESTION", nullable = false, columnDefinition = "TEXT")
    private String question;

    // 4. 답변 내용
    @Column(name = "ANSWER", nullable = false, columnDefinition = "TEXT")
    private String answer;

    // 노출 여부 (필요 시 사용)
    @Column(name = "IS_VISIBLE")
    private boolean isVisible;

    @Builder
    public Faq(InquiryType type, String category, String question, String answer) {
        this.type = type;
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.isVisible = true; // 기본값 노출
    }

    public void update(InquiryType type, String category, String question, String answer, boolean isVisible) {
        this.type = type;
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.isVisible = isVisible;
    }
}
