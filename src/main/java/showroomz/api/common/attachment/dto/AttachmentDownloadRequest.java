package showroomz.api.common.attachment.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.global.utils.AllowedAttachmentExtensions;

import java.util.List;

/**
 * 다운로드 URL 일괄 발급 요청 — 파일 하나를 누를 때도, 메시지의 「전체 다운로드」도 이 요청 하나다.
 * 상한은 메시지 1건의 첨부 상한과 같다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDownloadRequest {

    @ArraySchema(arraySchema = @Schema(description = "내려받을 첨부 ID — 응답이 이 순서를 따른다. 중복은 한 번만 발급한다. "
            + "「전체 다운로드」는 메시지의 attachments를 sortOrder 순서 그대로 넣는다",
            example = "[501, 502, 503]", requiredMode = Schema.RequiredMode.REQUIRED),
            minItems = 1, maxItems = AllowedAttachmentExtensions.MAX_ATTACHMENT_COUNT)
    @NotEmpty
    @Size(max = AllowedAttachmentExtensions.MAX_ATTACHMENT_COUNT)
    private List<@NotNull Long> attachmentIds;
}
