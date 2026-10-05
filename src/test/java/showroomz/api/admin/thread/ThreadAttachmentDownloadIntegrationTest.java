package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.support.BrandFixture;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 첨부 다운로드 — URL 일괄 발급 + FE 순차 다운로드(§13-8 · §13-9).
 *
 * <p>파트너센터 · 쇼룸 스튜디오 · 어드민이 같은 규칙이다. {@code POST /v1/{seller|creator|admin}/attachments/download}에
 * 첨부 ID 목록을 보내면 같은 순서로 presigned URL 목록이 온다 — 파일 하나도, 메시지의 「전체 다운로드」도 이 요청 하나다.
 * 파일은 서버를 거치지 않고 S3에서 바로 내려온다(서버 압축 없음).
 *
 * <p>핵심 규칙 — ① 요청 순서를 지키고 중복은 한 번만 ② <b>전부 되거나 전부 안 된다</b>(하나라도 막히면 서명을 하나도 만들지 않는다)
 * ③ 스레드 권한은 서피스별 판정 그대로 ④ 최대 20개.
 */
@DisplayName("[통합] 첨부 다운로드 — URL 일괄 발급 (브랜드 · 인플루언서 · 어드민)")
class ThreadAttachmentDownloadIntegrationTest extends AdminThreadTestSupport {

    @Autowired private MessageThreadService messageThreadService;

    // ------------------------------------------------------------------ 전체 다운로드

    @Test
    @DisplayName("전체 다운로드 — 메시지의 첨부를 sortOrder 순서로 보내면 같은 순서로 URL이 온다. 보낸 쪽 · 받은 쪽 모두 받는다")
    void downloadsWholeMessage() throws Exception {
        List<MessageAttachment> files = sendWithAttachments(thread, ParticipantType.SELLER, brand.marketId(),
                "촬영본.mp4", "촬영가이드.pdf", "레퍼런스.png");
        List<Long> ids = idsOf(files);

        downloadUrls("creator", creatorToken, ids)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].attachmentId").value(contains(ids.get(0).intValue(), ids.get(1).intValue(), ids.get(2).intValue())))
                .andExpect(jsonPath("$[*].originalName").value(contains("촬영본.mp4", "촬영가이드.pdf", "레퍼런스.png")))
                .andExpect(jsonPath("$[0].downloadUrl").value(downloadUrlOf(files.get(0))))
                .andExpect(jsonPath("$[1].downloadUrl").value(downloadUrlOf(files.get(1))))
                .andExpect(jsonPath("$[2].downloadUrl").value(downloadUrlOf(files.get(2))))
                .andExpect(jsonPath("$[0].sizeBytes").value(1_000))
                .andExpect(jsonPath("$[0].expiresInSeconds").value(300));

        downloadUrls("seller", brandToken, ids).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("파일 하나를 누를 때도 같은 API다 — ID 하나를 보내면 URL 하나가 온다")
    void downloadsSingleFile() throws Exception {
        List<MessageAttachment> files = sendWithAttachments(thread, ParticipantType.CREATOR, creator.getId(),
                "계약서.pdf", "사업자등록증.pdf");

        downloadUrls("seller", brandToken, List.of(files.get(1).getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].attachmentId").value(files.get(1).getId()))
                .andExpect(jsonPath("$[0].originalName").value("사업자등록증.pdf"));
    }

    @Test
    @DisplayName("응답은 저장 순서가 아니라 요청 순서를 따르고, 같은 ID는 한 번만 발급한다")
    void followsRequestOrderAndDedupes() throws Exception {
        List<MessageAttachment> files = sendWithAttachments(thread, ParticipantType.SELLER, brand.marketId(),
                "a.pdf", "b.pdf", "c.pdf");
        Long a = files.get(0).getId(), b = files.get(1).getId(), c = files.get(2).getId();

        downloadUrls("creator", creatorToken, List.of(c, a, c, b, a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].originalName").value(contains("c.pdf", "a.pdf", "b.pdf")));
    }

    @Test
    @DisplayName("한 요청에 여러 스레드의 첨부가 섞여도 각 스레드가 내 것이면 된다")
    void mixesThreadsTheMemberBelongsTo() throws Exception {
        MessageAttachment fromPair = sendWithAttachments(thread, ParticipantType.CREATOR, creator.getId(), "pair.pdf").get(0);
        MessageAttachment fromOperator = sendWithAttachments(brandChannel, ParticipantType.ADMIN, admin.getId(), "notice.pdf").get(0);

        downloadUrls("seller", brandToken, List.of(fromOperator.getId(), fromPair.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].originalName").value(contains("notice.pdf", "pair.pdf")));
    }

    @Test
    @DisplayName("한 메시지의 상한(20개)까지 한 번에 받는다")
    void downloadsUpToTwenty() throws Exception {
        String[] names = LongStream.rangeClosed(1, 20).mapToObj(i -> "file-" + i + ".pdf").toArray(String[]::new);
        List<MessageAttachment> files = sendWithAttachments(thread, ParticipantType.SELLER, brand.marketId(), names);

        downloadUrls("creator", creatorToken, idsOf(files))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[19].originalName").value("file-20.pdf"));
    }

    // ------------------------------------------------------------------ 전부 아니면 전무

    @Test
    @DisplayName("남의 스레드 첨부가 하나라도 섞이면 403이고 서명을 하나도 만들지 않는다")
    void otherThreadPoisonsWholeRequest() throws Exception {
        MessageAttachment mine = sendWithAttachments(thread, ParticipantType.SELLER, brand.marketId(), "mine.pdf").get(0);
        MessageAttachment others = sendWithAttachments(otherPairThread(), ParticipantType.SELLER, null, "others.pdf").get(0);

        downloadUrls("creator", creatorToken, List.of(mine.getId(), others.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("없는 첨부가 섞이면 403이다 — 어느 것이 없는지 알려주지 않는다")
    void missingIdPoisonsWholeRequest() throws Exception {
        MessageAttachment mine = sendWithAttachments(thread, ParticipantType.SELLER, brand.marketId(), "mine.pdf").get(0);

        downloadUrls("creator", creatorToken, List.of(mine.getId(), 999_999L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_ACCESS_DENIED"));
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("상대가 아직 보내지 않은 첨부가 섞이면 403이다 — 내가 올린 전송 전 첨부는 받을 수 있다")
    void unsentAttachmentIsUploaderOnly() throws Exception {
        MessageAttachment sent = sendWithAttachments(thread, ParticipantType.SELLER, brand.marketId(), "sent.pdf").get(0);
        MessageAttachment creatorDraft = uploadedAttachment(thread, ParticipantType.CREATOR, creator.getId(),
                "작업중.pdf", AttachmentType.DOCUMENT, 1_000L);

        downloadUrls("seller", brandToken, List.of(sent.getId(), creatorDraft.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_ACCESS_DENIED"));
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));

        downloadUrls("creator", creatorToken, List.of(sent.getId(), creatorDraft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ------------------------------------------------------------------ 서피스별 스레드 권한

    @Test
    @DisplayName("브랜드 — 다른 브랜드의 운영팀 채널 · 남의 쌍 스레드 첨부는 받을 수 없다")
    void sellerScope() throws Exception {
        BrandFixture.Brand mood = brandWithChannel("mood@showroomz.test", "무드코스메틱");
        MessageAttachment moodNotice = sendWithAttachments(channelOf(mood.market()), ParticipantType.ADMIN, admin.getId(),
                "mood.pdf").get(0);

        downloadUrls("seller", brandToken, List.of(moodNotice.getId())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        downloadUrls("seller", sellerToken(mood.seller()), List.of(moodNotice.getId())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("인플루언서 — 브랜드의 운영팀 채널 첨부는 받을 수 없고 자기 운영팀 채널 첨부는 받는다")
    void creatorScope() throws Exception {
        MessageAttachment brandNotice = sendWithAttachments(brandChannel, ParticipantType.ADMIN, admin.getId(), "b.pdf").get(0);
        MessageAttachment creatorNotice = sendWithAttachments(creatorChannel, ParticipantType.ADMIN, admin.getId(), "c.pdf").get(0);

        downloadUrls("creator", creatorToken, List.of(brandNotice.getId())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        downloadUrls("creator", creatorToken, List.of(creatorNotice.getId())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("어드민 — 운영팀 채널 첨부는 여러 채널을 섞어 받고, 쌍 스레드 첨부는 받을 수 없다")
    void adminScope() throws Exception {
        MessageAttachment fromBrand = sendWithAttachments(brandChannel, ParticipantType.SELLER, brand.marketId(), "brand.pdf").get(0);
        MessageAttachment fromCreator = sendWithAttachments(creatorChannel, ParticipantType.CREATOR, creator.getId(), "creator.pdf").get(0);
        MessageAttachment fromPair = sendWithAttachments(thread, ParticipantType.SELLER, brand.marketId(), "pair.pdf").get(0);

        downloadUrls("admin", adminToken, List.of(fromBrand.getId(), fromCreator.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].originalName").value(contains("brand.pdf", "creator.pdf")));
        downloadUrls("admin", otherAdminToken, List.of(fromBrand.getId(), fromPair.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("다른 서피스의 엔드포인트는 쓸 수 없고 토큰이 없으면 401이다")
    void surfaceEndpointsAreRoleBound() throws Exception {
        MessageAttachment file = sendWithAttachments(brandChannel, ParticipantType.ADMIN, admin.getId(), "a.pdf").get(0);

        downloadUrls("admin", brandToken, List.of(file.getId())).andExpect(status().isForbidden());
        downloadUrls("admin", creatorToken, List.of(file.getId())).andExpect(status().isForbidden());
        downloadUrls("creator", brandToken, List.of(file.getId())).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/seller/attachments/download").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(java.util.Map.of("attachmentIds", List.of(file.getId())))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 요청 형식

    @Test
    @DisplayName("요청 형식 — 비었거나 · 21개 이상이거나 · null이 섞이거나 · 필드가 없으면 400이다")
    void validatesRequest() throws Exception {
        for (String surface : List.of("seller", "creator", "admin")) {
            String token = switch (surface) {
                case "seller" -> brandToken;
                case "creator" -> creatorToken;
                default -> adminToken;
            };
            downloadUrls(surface, token, List.of()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            downloadUrls(surface, token, LongStream.rangeClosed(1, 21).boxed().toList()).andExpect(status().isBadRequest());
            mockMvc.perform(post("/v1/" + surface + "/attachments/download").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"attachmentIds\":[1,null]}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/v1/" + surface + "/attachments/download").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("단건 GET 다운로드는 더 이상 없다 — 일괄 발급으로 대체됐다")
    void singleGetEndpointIsGone() throws Exception {
        MessageAttachment file = sendWithAttachments(thread, ParticipantType.SELLER, brand.marketId(), "a.pdf").get(0);

        mockMvc.perform(get("/v1/seller/attachments/" + file.getId() + "/download").header(HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/creator/attachments/" + file.getId() + "/download").header(HttpHeaders.AUTHORIZATION, creatorToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/admin/attachments/" + file.getId() + "/download").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 적재

    /**
     * 업로드가 끝난 첨부를 만들어 한 메시지로 보낸다 — 운영 전송 경로(소유권 · 순서 · 조건부 연결)를 그대로 탄다.
     * 반환 순서가 곧 메시지의 sortOrder다.
     */
    private List<MessageAttachment> sendWithAttachments(MessageThread channel, ParticipantType senderType, Long senderId,
                                                        String... fileNames) {
        Long uploaderId = senderId != null ? senderId : channel.getConnection().getMarket().getId();
        List<MessageAttachment> files = new ArrayList<>();
        for (String name : fileNames) {
            files.add(uploadedAttachment(channel, senderType, uploaderId, name, typeOf(name), 1_000L));
        }
        transactionTemplate.executeWithoutResult(tx -> messageThreadService.sendMessage(
                threadRepository.findById(channel.getId()).orElseThrow(), senderType, uploaderId,
                UUID.randomUUID().toString(), null, idsOf(files)));
        return files;
    }

    private static AttachmentType typeOf(String fileName) {
        if (fileName.endsWith(".png")) return AttachmentType.IMAGE;
        if (fileName.endsWith(".mp4")) return AttachmentType.VIDEO;
        return AttachmentType.DOCUMENT;
    }

    private static List<Long> idsOf(List<MessageAttachment> files) {
        return files.stream().map(MessageAttachment::getId).toList();
    }

    /** 글로우랩 · 뷰티_하윤이 모두 끼지 않은 쌍 — 다른 브랜드 「퓨어랩」과 다른 인플루언서 「민지의 쇼룸」. */
    private MessageThread otherPairThread() {
        BrandFixture.Brand pure = fixture.createBrand("pure@showroomz.test", "퓨어랩");
        Creator minji = createCreator("민지의 쇼룸", "minji");
        Connection pair = Connection.requestPair(pure.market(), minji);
        pair.markConnected();
        connections.save(pair);
        return inTransaction(() -> threadRepository.save(MessageThread.openFor(
                connections.findById(pair.getId()).orElseThrow())));
    }
}
