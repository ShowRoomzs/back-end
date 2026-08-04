package showroomz.api.admin.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;

@Getter
@Setter
@Schema(description = "크리에이터 지원서 목록 검색 조건")
public class CreatorApplicationSearchCondition {

    @Schema(
            description = "신청 상태 필터 (PENDING: 심사대기, APPROVED: 승인, REJECTED: 반려, 미입력 시 전체)",
            example = "PENDING",
            allowableValues = {"PENDING", "APPROVED", "REJECTED"}
    )
    private CreatorApplicationStatus status;

    @Schema(description = "검색어 (활동명·계정 아이디 부분 일치 OR 검색)", example = "뷰티마스터")
    private String keyword;
}
