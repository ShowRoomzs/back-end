package showroomz.api.creator.groupbuy;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.groupbuy.GroupBuyTestSupport;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.repository.GroupBuyFulfillmentCheckRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRevisionRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * §31 스튜디오 공구 관리 테스트의 공통 배선 — 파트너 픽스처(브랜드 1 · 연결된 인플루언서 1 · 상품 2)를 그대로 쓰고
 * 그 인플루언서의 토큰으로 {@code /v1/creator/group-buys}를 부른다.
 */
abstract class CreatorGroupBuyTestSupport extends GroupBuyTestSupport {

    protected static final String STUDIO = "/v1/creator/group-buys";

    @Autowired protected GroupBuyPostRevisionRepository revisionRepository;
    @Autowired protected GroupBuyFulfillmentCheckRepository fulfillmentCheckRepository;

    protected String creatorToken;

    @BeforeEach
    void setUpCreatorToken() {
        creatorToken = tokenOf(creator);
    }

    protected String tokenOf(Creator owner) {
        Users user = owner.getUser();
        return bearerToken(user.getUsername(), RoleType.CREATOR, user.getId());
    }

    // ------------------------------------------------------------------ 요청

    protected ResultActions studioList(String query) throws Exception {
        return mockMvc.perform(get(STUDIO + (query == null ? "" : "?" + query))
                .header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    protected ResultActions studioSummary() throws Exception {
        return mockMvc.perform(get(STUDIO + "/summary").header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    protected ResultActions studioDetail(long groupBuyId) throws Exception {
        return studioDetail(groupBuyId, null);
    }

    protected ResultActions studioDetail(long groupBuyId, String query) throws Exception {
        return mockMvc.perform(get(STUDIO + "/" + groupBuyId + (query == null ? "" : "?" + query))
                .header(HttpHeaders.AUTHORIZATION, creatorToken));
    }

    protected ResultActions saveDraft(long groupBuyId, Object body) throws Exception {
        return send(put(STUDIO + "/" + groupBuyId + "/post/draft"), body);
    }

    protected ResultActions submitPost(long groupBuyId, Object body) throws Exception {
        return send(post(STUDIO + "/" + groupBuyId + "/post/submission"), body);
    }

    protected ResultActions editPost(long groupBuyId, Object body) throws Exception {
        return send(patch(STUDIO + "/" + groupBuyId + "/post"), body);
    }

    protected ResultActions studioAction(long groupBuyId, String path, Object body) throws Exception {
        return send(post(STUDIO + "/" + groupBuyId + "/" + path), body);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request = request.header(HttpHeaders.AUTHORIZATION, creatorToken);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : toJson(body));
        }
        return mockMvc.perform(request);
    }

    // ------------------------------------------------------------------ 적재

    /** 브랜드의 연장 요청(C1) — 파트너 API가 만드는 값이다. */
    protected GroupBuyExtensionRequest seedExtension(Long groupBuyId, int days) {
        return transactionTemplate.execute(tx -> {
            GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId).orElseThrow();
            return extensionRequestRepository.save(GroupBuyExtensionRequest.request(
                    groupBuy, brand.seller().getId(), days, "수요 증가", LocalDateTime.now().withNano(0).minusHours(2)));
        });
    }
}
