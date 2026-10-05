package showroomz.api.admin.thread;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 첨부 3단계 — presign → S3 직접 PUT → complete, 그리고 다운로드(36 설계 3-8 · (1) §13-7 ~ §13-11).
 *
 * <p>규칙은 파트너센터 · 스튜디오와 같고 업로더만 운영자다. S3는 외부 의존이라 서명기 · HeadObject를 모의한다 —
 * 이 클래스만 S3 빈을 바꾸므로 별도 테스트 컨텍스트로 돈다.
 */
@DisplayName("[통합] 어드민 소통 스레드 — 첨부 업로드 · 다운로드")
class AdminThreadAttachmentIntegrationTest extends AdminThreadTestSupport {

    @MockitoBean private S3Presigner s3Presigner;
    @MockitoBean private S3Client s3Client;

    @BeforeEach
    void stubS3() throws Exception {
        PresignedPutObjectRequest put = mock(PresignedPutObjectRequest.class);
        given(put.url()).willReturn(URI.create("https://s3.test/upload").toURL());
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(put);
        PresignedGetObjectRequest download = mock(PresignedGetObjectRequest.class);
        given(download.url()).willReturn(URI.create("https://s3.test/download").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(download);
        givenUploaded(1_200_000L, "application/pdf");
    }

    private void givenUploaded(long size, String contentType) {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(size).contentType(contentType).build());
    }

    // ------------------------------------------------------------------ 3단계 전체

    @Test
    @DisplayName("운영자가 올린 파일이 상대에게 그대로 간다 — presign · complete · 전송 · 상대 다운로드까지")
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
        mockMvc.perform(get("/v1/seller/attachments/" + attachmentId + "/download").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://s3.test/download"))
                .andExpect(jsonPath("$.originalName").value("9월_정산내역.pdf"));
        download(attachmentId, otherAdminToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("상대가 보낸 첨부를 운영자가 받는다")
    void operatorDownloadsMemberAttachment() throws Exception {
        MessageAttachment fromSeller = uploadedAttachment(brandChannel, ParticipantType.SELLER, brand.marketId(),
                "입점서류.pdf", AttachmentType.DOCUMENT, 2_000L);
        mockMvc.perform(post("/v1/seller/threads/" + brandChannel.getId() + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, brandToken).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("clientMessageId", UUID.randomUUID().toString(),
                                "attachmentIds", List.of(fromSeller.getId())))))
                .andExpect(status().isCreated());

        download(fromSeller.getId(), adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentId").value(fromSeller.getId()))
                .andExpect(jsonPath("$.originalName").value("입점서류.pdf"));
    }

    // ------------------------------------------------------------------ presign 거절

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

    // ------------------------------------------------------------------ complete

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
    @DisplayName("complete 전(PENDING) 첨부는 메시지에 붙일 수 없다")
    void pendingAttachmentCannotBeSent() throws Exception {
        long attachmentId = readLong(body(presign(brandChannel, adminToken, "a.pdf", "application/pdf", 1_000L)),
                "$.attachmentId");

        adminSend(brandChannel.getId(), adminToken, UUID.randomUUID().toString(), null, List.of(attachmentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_UPLOADED"));
    }

    // ------------------------------------------------------------------ 다운로드 권한

    @Test
    @DisplayName("다운로드 — 쌍 스레드의 첨부는 403이고 S3 서명을 만들지 않는다")
    void pairThreadAttachmentIsNotDownloadable() throws Exception {
        MessageAttachment pair = uploadedAttachment(thread, ParticipantType.SELLER, brand.marketId(),
                "촬영가이드.pdf", AttachmentType.DOCUMENT, 1_000L);

        download(pair.getId(), adminToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        download(999_999L, adminToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_ACCESS_DENIED"));
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("다운로드 — 아직 보내지 않은 첨부는 올린 운영자만 받는다. 같은 운영팀의 다른 운영자도 전송 전에는 못 받는다")
    void unsentAttachmentIsUploaderOnly() throws Exception {
        MessageAttachment draft = uploadedAttachment(brandChannel, ParticipantType.ADMIN, admin.getId(),
                "초안.pdf", AttachmentType.DOCUMENT, 1_000L);

        download(draft.getId(), adminToken).andExpect(status().isOk());
        download(draft.getId(), otherAdminToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_ACCESS_DENIED"));
        mockMvc.perform(get("/v1/seller/attachments/" + draft.getId() + "/download").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
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

    private ResultActions download(long attachmentId, String token) throws Exception {
        return mockMvc.perform(get("/v1/admin/attachments/" + attachmentId + "/download").header(HttpHeaders.AUTHORIZATION, token));
    }
}
