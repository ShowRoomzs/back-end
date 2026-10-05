package showroomz.api.admin.thread;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.admin.contract.AdminContractTestSupport;
import showroomz.api.admin.thread.service.AdminThreadCommandService;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.connection.service.OperatorChannelService;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.type.AttachmentType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.support.BrandFixture;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ui-admin-20a-channels(rev.1) 어드민 소통 스레드 테스트의 공통 배선(36 설계서).
 *
 * <p>계약 관리 테스트의 당사자를 그대로 쓴다 — 브랜드 「글로우랩」 · 인플루언서 「뷰티_하윤」(PAIR 연결 · 스레드 있음) ·
 * 운영자 「김운영」. 여기에 두 사람의 <b>운영팀 채널</b>과 두 번째 운영자 「박운영」을 더한다 — 운영팀 공용 읽음 ·
 * 운영자 이름 분리 · 동시 클릭은 운영자가 둘이어야 확인된다.
 *
 * <p>브랜드 채널은 안내 메시지 없이 빈 스레드로, 인플루언서 채널은 가입 안내(시스템 발신) 1건으로 열린다 —
 * 운영 코드의 채널 생성 경로({@link OperatorChannelService})를 그대로 탄다.
 */
public abstract class AdminThreadTestSupport extends AdminContractTestSupport {

    protected static final String ADMIN_CHANNELS = "/v1/admin/connections/threads";
    protected static final String ADMIN_SUMMARY = "/v1/admin/connections/summary";
    protected static final String ADMIN_THREADS = "/v1/admin/threads/";
    protected static final String OTHER_OPERATOR_NAME = "박운영";
    protected static final String NOTICE = "모두싸인에서 서명 안내를 다시 보내드렸습니다. 메일함(스팸함 포함)을 확인해 주세요.";
    protected static final String RESEND_CARD_TITLE = "요청 · 서명 안내 다시 받기";
    protected static final String CANCEL_CARD_TITLE = "계약 직권 취소 처리됨";
    protected static final String CREATOR_WELCOME = "안녕하세요, 뷰티_하윤님. 아직 연결된 브랜드가 없네요. "
            + "브랜드가 연결 요청을 보내면 [요청함] 탭에서 확인하실 수 있어요.";

    @Autowired protected OperatorChannelService operatorChannels;
    @Autowired protected MessageRepository messages;
    @Autowired protected MessageThreadRepository threadRepository;
    @Autowired protected MessageAttachmentRepository attachmentRepository;
    @Autowired protected AdminThreadCommandService threadCommands;

    /**
     * S3는 외부 의존이라 서명기 · HeadObject를 모의한다. 첨부를 다루지 않는 테스트도 같은 빈 구성을 써야
     * 어드민 스레드 테스트 전체가 컨텍스트 하나를 공유한다.
     */
    @MockitoBean protected S3Presigner s3Presigner;
    @MockitoBean protected S3Client s3Client;

    protected MessageThread brandChannel;
    protected MessageThread creatorChannel;
    protected Seller otherAdmin;
    protected String otherAdminToken;

    @BeforeEach
    void setUpOperatorChannels() throws Exception {
        stubS3();
        brandChannel = channelOf(brand.market());
        creatorChannel = channelOf(creator);
        otherAdmin = fixture.createAdmin("operator2@showroomz.test", OTHER_OPERATOR_NAME);
        otherAdminToken = adminToken(otherAdmin);
    }

    // ------------------------------------------------------------------ S3

    /** 업로드 URL · 다운로드 URL은 고정 값, HeadObject는 1.2MB PDF로 답한다. 다운로드 URL에는 S3 키가 실린다. */
    private void stubS3() throws Exception {
        PresignedPutObjectRequest put = mock(PresignedPutObjectRequest.class);
        given(put.url()).willReturn(URI.create("https://s3.test/upload").toURL());
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(put);
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willAnswer(invocation -> {
            GetObjectPresignRequest request = invocation.getArgument(0);
            PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
            given(signed.url()).willReturn(URI.create("https://s3.test/download/" + request.getObjectRequest().key()).toURL());
            return signed;
        });
        givenUploaded(1_200_000L, "application/pdf");
    }

    protected void givenUploaded(long size, String contentType) {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(size).contentType(contentType).build());
    }

    protected static String downloadUrlOf(MessageAttachment attachment) {
        return "https://s3.test/download/" + attachment.getS3Key();
    }

    // ------------------------------------------------------------------ 적재

    /** 브랜드의 운영팀 채널 — 없으면 입점 승인 훅과 같은 경로로 만든다. */
    protected MessageThread channelOf(Market market) {
        return inTransaction(() -> operatorChannels.requireMarketChannel(market));
    }

    /** 인플루언서의 운영팀 채널 — 없으면 등록 완료 훅과 같은 경로(가입 안내 1건)로 만든다. */
    protected MessageThread channelOf(Creator creator) {
        return inTransaction(() -> operatorChannels.requireCreatorChannel(creator));
    }

    /** 운영팀 채널을 가진 브랜드를 하나 더 만든다. */
    protected BrandFixture.Brand brandWithChannel(String email, String marketName) {
        BrandFixture.Brand other = fixture.createBrand(email, marketName);
        channelOf(other.market());
        return other;
    }

    /** 인플루언서의 로그인 토큰 — 계정의 username이 {@code creator-{accountId}}다. */
    protected String creatorTokenOf(Creator target) {
        return bearerToken(target.getUser().getUsername(), RoleType.CREATOR, target.getUser().getId());
    }

    /**
     * 목록 정렬은 {@code last_message_at}이 정한다. 연속 전송은 같은 시각을 받을 수 있어 정렬 검증에는
     * 시각을 직접 박는다(애플리케이션에 시간을 되감을 방법이 없다).
     */
    protected void backdateLastMessage(MessageThread channel, LocalDateTime at) {
        jdbc.update("UPDATE message_thread SET last_message_at = ? WHERE thread_id = ?", at, channel.getId());
    }

    protected void setMarketStatus(BrandFixture.Brand target, String status) {
        jdbc.update("UPDATE market SET status = ? WHERE market_id = ?", status, target.marketId());
    }

    protected void setUserStatus(Creator target, String status) {
        jdbc.update("UPDATE users SET status = ? WHERE user_id = ?", status, target.getUser().getId());
    }

    /**
     * 업로드가 끝난(UPLOADED) 첨부를 S3 없이 적재한다 — presign · HeadObject는 외부 의존이라
     * 전송 · 조회 경로만 보는 테스트는 이 행으로 시작한다. 업로드 경로 자체는 첨부 테스트가 S3를 모의해 본다.
     */
    protected MessageAttachment uploadedAttachment(MessageThread channel, ParticipantType uploaderType, Long uploaderId,
                                                   String fileName, AttachmentType type, long sizeBytes) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        String key = "uploads/message/" + channel.getId() + "/" + UUID.randomUUID() + "." + extension;
        MessageAttachment attachment = MessageAttachment.pending(channel, uploaderType, uploaderId, type, key,
                "https://cdn.showroomz.test/" + key, fileName, extension, contentTypeOf(type), sizeBytes);
        attachment.markUploaded(sizeBytes, contentTypeOf(type), null);
        return attachmentRepository.save(attachment);
    }

    private static String contentTypeOf(AttachmentType type) {
        return switch (type) {
            case IMAGE -> "image/png";
            case VIDEO -> "video/mp4";
            case DOCUMENT -> "application/pdf";
        };
    }

    /** 가장 최근 재발송 요청. */
    protected ContractResendRequest latestResend(Contract contract) {
        return resends.findByContractIdOrderByRequestedAtDescIdDesc(contract.getId()).get(0);
    }

    protected long countMessagesWithContent(String content) {
        return messages.findAll().stream().filter(m -> content.equals(m.getContent())).count();
    }

    // ------------------------------------------------------------------ 어드민 요청

    protected ResultActions channels(String tab, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(ADMIN_CHANNELS).header(HttpHeaders.AUTHORIZATION, adminToken);
        if (tab != null) request.param("tab", tab);
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mockMvc.perform(request);
    }

    protected ResultActions summaryOf(String token) throws Exception {
        return mockMvc.perform(get(ADMIN_SUMMARY).header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions info(MessageThread channel) throws Exception {
        return info(channel.getId());
    }

    protected ResultActions info(Long threadId) throws Exception {
        return mockMvc.perform(get(ADMIN_THREADS + threadId + "/info").header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    protected ResultActions adminMessages(MessageThread channel, String... params) throws Exception {
        return adminMessages(channel.getId(), adminToken, params);
    }

    protected ResultActions adminMessages(Long threadId, String token, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(ADMIN_THREADS + threadId + "/messages")
                .header(HttpHeaders.AUTHORIZATION, token);
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return mockMvc.perform(request);
    }

    protected ResultActions adminSend(MessageThread channel, String token, String content) throws Exception {
        return adminSend(channel.getId(), token, UUID.randomUUID().toString(), content, null);
    }

    protected ResultActions adminSend(MessageThread channel, String token, String key, String content) throws Exception {
        return adminSend(channel.getId(), token, key, content, null);
    }

    protected ResultActions adminSend(Long threadId, String token, String key, String content,
                                      List<Long> attachmentIds) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (key != null) body.put("clientMessageId", key);
        if (content != null) body.put("content", content);
        if (attachmentIds != null) body.put("attachmentIds", attachmentIds);
        return mockMvc.perform(post(ADMIN_THREADS + threadId + "/messages")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions markRead(MessageThread channel, String token) throws Exception {
        return mockMvc.perform(post(ADMIN_THREADS + channel.getId() + "/read").header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions resendNotice(MessageThread channel, Long cardMessageId, String token) throws Exception {
        return resendNotice(channel.getId(), cardMessageId, token);
    }

    protected ResultActions resendNotice(Long threadId, Long cardMessageId, String token) throws Exception {
        return mockMvc.perform(post(ADMIN_THREADS + threadId + "/cards/" + cardMessageId + "/resend-notice")
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    /** 다운로드 URL 일괄 발급 — 어드민 · 파트너센터 · 스튜디오가 같은 모양이다. */
    protected ResultActions downloadUrls(String surface, String token, List<Long> attachmentIds) throws Exception {
        return mockMvc.perform(post("/v1/" + surface + "/attachments/download")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("attachmentIds", attachmentIds))));
    }

    // ------------------------------------------------------------------ 당사자 요청

    protected ResultActions sellerSend(MessageThread channel, String content) throws Exception {
        return mockMvc.perform(post("/v1/seller/threads/" + channel.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, brandToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content))));
    }

    protected ResultActions creatorSend(MessageThread channel, String content) throws Exception {
        return mockMvc.perform(post("/v1/creator/threads/" + channel.getId() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, creatorToken).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content))));
    }

    protected ResultActions sellerMessages(MessageThread channel) throws Exception {
        return mockMvc.perform(get("/v1/seller/threads/" + channel.getId() + "/messages").header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions creatorMessages(MessageThread channel) throws Exception {
        return creatorMessages(channel, creatorToken);
    }

    protected ResultActions creatorMessages(MessageThread channel, String token) throws Exception {
        return mockMvc.perform(get("/v1/creator/threads/" + channel.getId() + "/messages").header(HttpHeaders.AUTHORIZATION, token));
    }

    protected ResultActions sellerSummary() throws Exception {
        return mockMvc.perform(get("/v1/seller/connections/summary").header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions creatorSummary() throws Exception {
        return mockMvc.perform(get("/v1/creator/connections/summary").header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    protected ResultActions requestResendBySeller(Contract c) throws Exception {
        return mockMvc.perform(post(SELLER_CONTRACTS + "/" + c.getId() + "/resend-request").header(HttpHeaders.AUTHORIZATION, brandToken));
    }

    protected ResultActions requestResendByCreator(Contract c) throws Exception {
        return requestResendByCreator(c, creatorToken);
    }

    protected ResultActions requestResendByCreator(Contract c, String token) throws Exception {
        return mockMvc.perform(post(CREATOR_CONTRACTS + "/" + c.getId() + "/resend-request").header(HttpHeaders.AUTHORIZATION, token));
    }

    // ------------------------------------------------------------------ 응답 읽기

    /** 그 채널의 시스템 카드 메시지 id — 최신순. */
    protected List<Long> cardsIn(MessageThread channel) throws Exception {
        List<Number> ids = JsonPath.parse(body(adminMessages(channel)))
                .read("$.content[?(@.messageType == 'SYSTEM')].messageId");
        return ids.stream().map(Number::longValue).toList();
    }
}
