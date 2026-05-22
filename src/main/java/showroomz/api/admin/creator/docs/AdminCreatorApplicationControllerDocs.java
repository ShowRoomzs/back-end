package showroomz.api.admin.creator.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.admin.creator.dto.CreatorApplicationRejectRequest;
import showroomz.api.admin.creator.dto.CreatorApplicationResponse;
import showroomz.api.app.auth.DTO.ErrorResponse;

@Tag(name = "Admin - Creator Application", description = "크리에이터 지원서 관리 API")
@SecurityRequirement(name = "Authorization")
public interface AdminCreatorApplicationControllerDocs {

    @Operation(summary = "크리에이터 지원서 목록 조회", description = "관리자용 크리에이터 지원서 페이징 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Page<CreatorApplicationResponse>> getApplications(Pageable pageable);

    @Operation(summary = "크리에이터 지원 승인", description = "검수 대기(PENDING) 상태의 지원서를 승인하고 유저 권한을 CREATOR로 승격합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "승인 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 신청 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "신청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> approveApplication(@PathVariable Long applicationId);

    @Operation(summary = "크리에이터 지원 반려", description = "검수 대기(PENDING) 상태의 지원서를 반려하고 안내 메일을 발송합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "반려 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 신청 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "신청 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> rejectApplication(
            @PathVariable Long applicationId,
            @RequestBody CreatorApplicationRejectRequest request);
}
