package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.ParticipantType;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영자의 첨부 업로드 3단계 — presign → S3 직접 PUT → complete → 전송(36 설계 3-8 · (1) §13-7 ~ §13-11).
 *
 * <p>규칙은 파트너센터 · 스튜디오와 같고 업로더만 운영자다. 다운로드(URL 일괄 발급)는
 * {@link ThreadAttachmentDownloadIntegrationTest}가 세 서피스를 함께 본다.
 */
@DisplayName("[통합] 어드민 소통 스레드 — 첨부 업로드")
class AdminThreadAttachmentIntegrationTest extends AdminThreadTestSupport {

    @Test
    @DisplayName("운영자가 올린 파일이 상대에게 그대로 간다 — presign · complete · 전송 · 상대가 일괄 발급으로 받기까지")
    void operatorUploadReachesMember() throws Exception {
        String presigned = body(presign(brandChannel, adminToken, "9월_정산내역.pdf", "application/pdf", 1_200_000L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.test/upload"))
                .andExpect(jsonPath("$.requiredContentType").value("application/pdf")));
        long attachmentId = readLong(presigned, "$.attachmentId");

        MessageAttachment pending = attachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(pending.getUploaderType()).isEqualTo(ParticipantType.ADMIN);
        assertThat(pending.getUploaderId()).isEqualTo(admin.getId());
        assertThat(pending.getS3Key()).startsWith("uploads/message/" + brandChannel.getId() + "/");

        complete(attachmentId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.attachmentType").value("DOCUMENT"))
                .andExpect(jsonPath("$.sizeBytes").value(1_200_000));

        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), "9월 정산 내역 첨부드립니다.",
                List.of(attachmentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments", hasSize(1)))
                .andExpect(jsonPath("$.attachments[0].originalName").value("9월_정산내역.pdf"));

        sellerMessages(brandChannel).andExpect(jsonPath("$.content[0].attachments[0].attachmentId").value(attachmentId));
        downloadUrls("seller", brandToken, List.of(attachmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].downloadUrl").value(downloadUrlOf(pending)))
                .andExpect(jsonPath("$[0].originalName").value("9월_정산내역.pdf"));
        downloadUrls("admin", otherAdminToken, List.of(attachmentId)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("presign — 쌍 스레드 403 · 탈퇴 회원 채널 409 · 허용하지 않는 확장자 · 500MB 초과 400. 거절되면 행도 서명도 없다")
    void presignRejections() throws Exception {
        presign(thread, adminToken, "a.pdf", "application/pdf", 1_000L).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        presign(brandChannel, adminToken, "setup.exe", "application/octet-stream", 1_000L).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_EXTENSION_NOT_ALLOWED"));
        presign(brandChannel, adminToken, "huge.mp4", "video/mp4", 500L * 1024 * 1024 + 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_SIZE_EXCEEDED"));
        setUserStatus(creator, "WITHDRAWN");
        presign(creatorChannel, adminToken, "a.pdf", "application/pdf", 1_000L).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THREAD_READ_ONLY"));
        presign(brandChannel, brandToken, "a.pdf", "application/pdf", 1_000L).andExpect(status().isForbidden());

        assertThat(attachmentRepository.findAll()).isEmpty();
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("complete — 발급받은 운영자 본인만 한다. 다른 운영자는 같은 운영팀이어도 403이다")
    void onlyUploaderCompletes() throws Exception {
        long attachmentId = readLong(body(presign(brandChannel, adminToken, "a.pdf", "application/pdf", 1_200_000L)),
                "$.attachmentId");

        complete(attachmentId, otherAdminToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_ACCESS_DENIED"));
        assertThat(attachmentRepository.findById(attachmentId).orElseThrow().getStatus()).isEqualTo(AttachmentStatus.PENDING);

        complete(attachmentId, adminToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UPLOADED"));
        // 다시 통지해도 결과는 같다.
        complete(attachmentId, adminToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    @DisplayName("실제 올라간 파일이 선언과 다르면(이미지라더니 HTML) REJECTED가 되고 메시지에 붙일 수 없다")
    void mismatchedUploadIsRejected() throws Exception {
        long attachmentId = readLong(body(presign(brandChannel, adminToken, "photo.png", "image/png", 1_000L)),
                "$.attachmentId");
        givenUploaded(1_000L, "text/html");

        complete(attachmentId, adminToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), null, List.of(attachmentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_UPLOADED"));
    }

    @Test
    @DisplayName("complete 전(PENDING) 첨부는 메시지에 붙일 수도, 내려받을 수도 없다")
    void pendingAttachmentCannotBeSentOrDownloaded() throws Exception {
        long attachmentId = readLong(body(presign(brandChannel, adminToken, "a.pdf", "application/pdf", 1_000L)),
                "$.attachmentId");

        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), null, List.of(attachmentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_UPLOADED"));
        downloadUrls("admin", adminToken, List.of(attachmentId)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_UPLOADED"));
    }

    // ------------------------------------------------------------------ 요청

    private ResultActions presign(MessageThread channel, String token, String fileName, String contentType, long size)
            throws Exception {
        return mockMvc.perform(post(ADMIN_THREADS + channel.getId() + "/attachments/presign")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("fileName", fileName, "contentType", contentType, "sizeBytes", size))));
    }

    private ResultActions complete(long attachmentId, String token) throws Exception {
        return mockMvc.perform(patch("/v1/admin/attachments/" + attachmentId + "/complete")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content("{}"));
    }
}
