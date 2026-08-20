package showroomz.api.app.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import showroomz.domain.post.type.PostReportReason;
import showroomz.support.IntegrationTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C4 신고 시트의 인증 경계 — 어디까지가 열려 있고 어디부터 로그인인지.
 *
 * <p>사유 목록은 운영정책 문구일 뿐이라 누가 물어도 답이 같고, 접수는 사람당 1회를 세야 해서
 * 로그인이 필요하다. 둘이 같은 컨트롤러에 있어 화이트리스트가 한쪽으로 새기 쉬워 함께 본다.
 */
@DisplayName("[통합] C4 게시물 신고 인증 경계")
class UserPostReportIntegrationTest extends IntegrationTestSupport {

    private static final String REPORT_REASONS = "/v1/user/posts/report-reasons";
    private static final String REPORT_POST = "/v1/user/posts/{postId}/reports";

    @Test
    @DisplayName("신고 사유 목록은 토큰 없이도 열린다")
    void reportReasonsAreOpenToAnonymous() throws Exception {
        mockMvc.perform(get(REPORT_REASONS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(PostReportReason.values().length))
                .andExpect(jsonPath("$[0].code").value(PostReportReason.AD_DISCLOSURE.name()))
                .andExpect(jsonPath("$[0].label").value(PostReportReason.AD_DISCLOSURE.getLabel()))
                .andExpect(jsonPath("$[0].detailRequired").value(false));
    }

    /** 접수까지 함께 열리면 익명 신고가 대기열을 채운다 — 사유 목록만 열렸는지 확인한다. */
    @Test
    @DisplayName("신고 접수는 여전히 비로그인 401이다")
    void reportSubmissionStillRequiresLogin() throws Exception {
        mockMvc.perform(post(REPORT_POST, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reasonCode": "AD_DISCLOSURE" }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
