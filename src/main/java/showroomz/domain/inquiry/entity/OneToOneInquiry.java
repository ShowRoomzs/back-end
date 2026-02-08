package showroomz.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.InquiryType;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ONE_TO_ONE_INQUIRY")
public class OneToOneInquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INQUIRY_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    // 1. 문의 타입 (대분류 - Enum)
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private InquiryType type;

    // 2. 문의 유형 (상세 - String, 기획 미정으로 자유 입력)
    @Column(name = "CATEGORY", nullable = false, length = 50)
    private String category;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 이미지 URL 목록 (최대 10장)
    @ElementCollection
    @CollectionTable(
            name = "ONE_TO_ONE_INQUIRY_IMAGES",
            joinColumns = @JoinColumn(name = "INQUIRY_ID")
    )
    @Column(name = "IMAGE_URL", length = 512)
    private List<String> imageUrls = new ArrayList<>();

    // 답변 관련 필드
    @Column(name = "ANSWER_CONTENT", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "ANSWERED_AT")
    private LocalDateTime answeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private InquiryStatus status;

    @Builder
    public OneToOneInquiry(Users user, InquiryType type, String category, String content, List<String> imageUrls) {
        this.user = user;
        this.type = type;
        this.category = category; // 상세 유형 저장
        this.content = content;
        if (imageUrls != null) {
            this.imageUrls = imageUrls;
        }
        this.status = InquiryStatus.WAITING;
    }

    // (추후 어드민 기능 개발 시 사용) 답변 등록 메서드
    public void registerAnswer(String answerContent) {
        this.answerContent = answerContent;
        this.answeredAt = LocalDateTime.now();
        this.status = InquiryStatus.ANSWERED;
    }

    public void update(InquiryType type, String category, String content, List<String> imageUrls) {
        this.type = type;
        this.category = category;
        this.content = content;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
    }
}
