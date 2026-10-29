package showroomz.api.app.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C7 상품 상세 「문의」 탭의 한 행(58 계획 2-3 · 시안 C7) — 유형 배지 · 답변 상태 → 질문 → 작성자 · 날짜 → 답변 블록.
 * 비밀글은 작성자 본인에게만 본문 · 첨부 · 답변을 싣는다(판매자는 파트너센터에서 본다).
 */
@Getter
@Builder
@Schema(description = "공개 상품 문의 한 행")
public class PublicProductInquiryResponse {

    @Schema(description = "상품 문의 ID", example = "5021")
    private Long id;

    @Schema(description = "문의 유형 코드 (OPTION, INGREDIENT_USAGE, RESTOCK, DELIVERY, ETC)", example = "INGREDIENT_USAGE")
    private ProductInquiryType type;

    @Schema(description = "문의 유형 한글명", example = "성분·사용법")
    private String typeName;

    @Schema(description = "답변 상태 — WAITING(답변대기) · ANSWERED(답변완료)", example = "ANSWERED")
    private InquiryStatus status;

    @Schema(description = "비밀글 — 본인이 아니면 content · imageUrls · answer 가 비어 있다(「비밀글입니다」는 앱이 그린다)")
    private boolean secret;

    @Schema(description = "내가 쓴 문의 — 로그인한 소비자 본인이면 true")
    private boolean mine;

    @Schema(description = "문의 내용 — 비밀글이고 본인이 아니면 null", nullable = true)
    private String content;

    @Schema(description = "첨부 사진 URL — 비밀글이고 본인이 아니면 빈 목록")
    private List<String> imageUrls;

    @Schema(description = "작성자 닉네임 — 앞 절반만 노출하고 나머지는 *(수민초록 → 수민** · 지은맑 → 지**)", example = "수민**")
    private String authorName;

    @Schema(description = "문의 등록 일시")
    private LocalDateTime createdAt;

    @Schema(description = "답변 — 답변 전이거나 비밀글이고 본인이 아니면 null", nullable = true)
    private Answer answer;

    @Getter
    @Builder
    @Schema(name = "PublicProductInquiryAnswer", description = "브랜드 답변")
    public static class Answer {
        @Schema(description = "답변 내용")
        private String content;
        @Schema(description = "답변 일시")
        private LocalDateTime answeredAt;
    }

    /** @param viewerUserId 로그인한 소비자 id — 비로그인 · 소비자 아님이면 null */
    public static PublicProductInquiryResponse of(ProductInquiry inquiry, Long viewerUserId) {
        boolean mine = viewerUserId != null && inquiry.getUser() != null && viewerUserId.equals(inquiry.getUser().getId());
        boolean visible = !inquiry.isSecret() || mine;
        boolean answered = inquiry.getStatus() == InquiryStatus.ANSWERED && inquiry.getAnswerContent() != null;
        return PublicProductInquiryResponse.builder()
                .id(inquiry.getId())
                .type(inquiry.getType())
                .typeName(inquiry.getType().getDescription())
                .status(inquiry.getStatus())
                .secret(inquiry.isSecret())
                .mine(mine)
                .content(visible ? inquiry.getContent() : null)
                // 지연 로딩 컬렉션 — 트랜잭션 안에서 복사한다(ProductInquiryResponse 와 같은 이유)
                .imageUrls(visible ? List.copyOf(inquiry.getImageUrls()) : List.of())
                .authorName(maskHalf(inquiry.getUser() == null ? null : inquiry.getUser().getNickname()))
                .createdAt(inquiry.getCreatedAt())
                .answer(visible && answered ? Answer.builder().content(inquiry.getAnswerContent())
                        .answeredAt(inquiry.getAnsweredAt()).build() : null)
                .build();
    }

    /** 앞 절반(내림)만 남긴다 — 홀수 길이는 가리는 쪽을 더 많이(시안 C7 「공개 목록이라 줄이는 방향으로」). */
    static String maskHalf(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "알 수 없음";
        }
        String trimmed = nickname.trim();
        int[] codePoints = trimmed.codePoints().toArray();
        int shown = codePoints.length / 2;
        return new String(codePoints, 0, shown) + "*".repeat(codePoints.length - shown);
    }
}
