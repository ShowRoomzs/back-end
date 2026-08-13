package showroomz.api.admin.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.InquiryType;
import showroomz.global.dto.PaginationInfo;

import java.time.LocalDateTime;
import java.util.List;

/** §17 어드민 1:1 문의 응답·요청 스키마. */
public class AdminInquiryDto {

    @Getter
    @Builder
    @Schema(description = "목록 행 (§17-2 컬럼 7종)")
    public static class ListItem {
        @Schema(description = "문의 ID", example = "21")
        private Long inquiryId;
        @Schema(description = "유형 코드", example = "DELIVERY")
        private InquiryType type;
        @Schema(description = "유형 명", example = "배송")
        private String typeName;
        @Schema(description = "문의 내용 — 목록은 한 줄 말줄임으로 표시한다(제목 필드 없음)")
        private String content;
        @Schema(description = "작성자(소비자) 이름", example = "오세아")
        private String writerName;
        @Schema(description = "접수일시", example = "2026-07-15T11:20:00")
        private LocalDateTime createdAt;
        @Schema(description = "답변일시 — 미답변이면 null", example = "2026-07-16T10:12:00")
        private LocalDateTime answeredAt;
        @Schema(description = "경과 — 모든 행에 값이 있다. 미답변이면 현재까지, 답변 건이면 접수→답변 소요", example = "3일 2h")
        private String elapsedText;
        @Schema(description = "미답변 && 경과 3일 초과 — true면 상태 배지를 'SLA 초과'로 교체한다", example = "true")
        private boolean slaExceeded;
        @Schema(description = "상태", example = "WAITING")
        private InquiryStatus status;
    }

    @Getter
    @Builder
    @Schema(description = "상태 탭 건수 — 유형·검색어 필터는 반영하고 상태 조건만 제외한 값")
    public static class StatusCounts {
        @Schema(description = "접수(미답변)", example = "2")
        private long waiting;
        @Schema(description = "답변완료", example = "4")
        private long answered;
        @Schema(description = "전체", example = "6")
        private long all;
    }

    @Getter
    @Builder
    @Schema(description = "목록 응답")
    public static class ListResponse {
        private List<ListItem> content;
        private PaginationInfo pageInfo;
        @Schema(description = "상태 탭 건수 — 툴바의 `총 N건 · 미답변 N건`도 이 값으로 그린다")
        private StatusCounts statusCounts;
    }

    @Getter
    @Builder
    @Schema(description = "GNB 배지용 미답변 건수 (§17-7)")
    public static class SummaryResponse {
        @Schema(description = "미답변(접수) 건수", example = "2")
        private long unansweredCount;
    }

    @Getter
    @Builder
    @Schema(description = "유형 필터 옵션 (§17-2-1)")
    public static class TypeOption {
        @Schema(example = "DELIVERY")
        private InquiryType code;
        @Schema(example = "배송")
        private String label;
    }

    @Getter
    @Builder
    @Schema(description = "문의 스레드 메시지 (§17-3)")
    public static class ThreadMessage {
        @Schema(description = "USER(소비자) / OPERATOR(운영자)", example = "USER")
        private String role;
        @Schema(description = "작성자명", example = "김민서")
        private String authorName;
        @Schema(example = "2026-07-16T10:12:00")
        private LocalDateTime sentAt;
        private String content;
        @Schema(description = "첨부 사진 — 소비자 메시지에만 존재하며 최대 5장 (§17-5)")
        private List<String> imageUrls;
    }

    @Getter
    @Builder
    @Schema(description = "처리 이력 1건 — 문의 접수 / 답변 등록, 시간 역순")
    public static class HistoryEvent {
        @Schema(description = "RECEIVED / ANSWERED", example = "RECEIVED")
        private String event;
        @Schema(example = "2026-07-16T10:12:00")
        private LocalDateTime occurredAt;
        @Schema(example = "소비자(김민서)")
        private String actorLabel;
    }

    @Getter
    @Builder
    @Schema(description = "상세 응답 (§17-3)")
    public static class DetailResponse {
        @Schema(example = "21")
        private Long inquiryId;
        @Schema(description = "문의번호", example = "INQ-20260716-021")
        private String inquiryNumber;
        @Schema(example = "CANCEL_EXCHANGE_RETURN")
        private InquiryType type;
        @Schema(example = "취소/교환/반품")
        private String typeName;
        @Schema(example = "WAITING")
        private InquiryStatus status;
        @Schema(description = "미답변 && 경과 3일 초과", example = "false")
        private boolean slaExceeded;
        @Schema(description = "소비자 회원 ID — 회원 상세 링크용", example = "108")
        private Long userId;
        @Schema(example = "김민서")
        private String userName;
        @Schema(description = "참조 주문 ID — 선택값이라 없으면 null (화면에서는 `—`)", example = "456")
        private Long orderId;
        @Schema(description = "접수일시", example = "2026-07-16T10:12:00")
        private LocalDateTime createdAt;
        @Schema(description = "답변일시 — 미답변이면 null")
        private LocalDateTime answeredAt;
        @Schema(description = "경과 값", example = "2일 4h")
        private String elapsedText;
        @Schema(description = "경과 라벨 — 접수: 미답변 경과 / 답변완료: 응답 소요", example = "미답변 경과")
        private String elapsedLabel;
        @Schema(description = "처리자 — 답변완료 상태에서만 값이 있다", example = "김운영")
        private String operatorName;
        private List<ThreadMessage> thread;
        private List<HistoryEvent> history;
        @Schema(description = "현재 탭·필터 기준 이전 문의 ID")
        private Long prevInquiryId;
        @Schema(description = "현재 탭·필터 기준 다음 문의 ID")
        private Long nextInquiryId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "답변 등록 요청 (§17-4) — 소비자에게 가공 없이 그대로 노출된다")
    public static class AnswerRequest {

        @NotBlank(message = "답변 내용을 입력해주세요.")
        @Size(max = 2000, message = "답변은 2000자 이내로 입력해주세요.")
        @Schema(description = "답변 본문", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "안녕하세요, 고객님. 반품 접수 확인했습니다.")
        private String content;
    }

    @Getter
    @Builder
    @Schema(description = "답변 등록 응답")
    public static class AnswerResponse {
        @Schema(example = "21")
        private Long inquiryId;
        @Schema(example = "INQ-20260716-021")
        private String inquiryNumber;
        @Schema(example = "ANSWERED")
        private InquiryStatus status;
        @Schema(example = "2026-07-18T14:00:00")
        private LocalDateTime answeredAt;
        @Schema(example = "김운영")
        private String operatorName;
        @Schema(description = "답변 등록 후 남은 미답변 건수 — GNB 배지 갱신용", example = "1")
        private long unansweredCount;
    }
}
