package showroomz.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
public class OneToOneInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "INQUIRY_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false)
    private InquiryType type;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

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

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "MODIFIED_AT", nullable = false)
    private LocalDateTime modifiedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.modifiedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.modifiedAt = LocalDateTime.now();
    }

    @Builder
    public OneToOneInquiry(Users user, InquiryType type, String title, String content, List<String> imageUrls) {
        this.user = user;
        this.type = type;
        this.title = title;
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
}
