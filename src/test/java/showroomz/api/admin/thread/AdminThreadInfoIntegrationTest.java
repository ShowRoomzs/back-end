package showroomz.api.admin.thread;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyIssue;
import showroomz.domain.groupbuy.repository.GroupBuyIssueRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.ThreadKind;
import showroomz.support.BrandFixture;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스레드 헤더 · 접이식 정보 바(36 설계 3-4 · §36-2).
 *
 * <p>정보 바는 <b>이 화면에서 계산하지 않는다</b> — 계약 · 공구 · 연결 모듈의 집계를 읽는다. 집계할 모듈이 없는 값은
 * 0이 아니라 null이어야 한다(0은 「없음」이라는 사실이고 null은 「모름」이다).
 */
@DisplayName("[통합] 어드민 소통 스레드 — 헤더 · 정보 바")
class AdminThreadInfoIntegrationTest extends AdminThreadTestSupport {

    @Autowired private GroupBuyRepository groupBuys;
    @Autowired private GroupBuyIssueRepository issues;

    // ------------------------------------------------------------------ ① 정보

    @Test
    @DisplayName("브랜드 채널 — 담당자 이름 · 연락처 · 가입일이 오고, 인플루언서 칸은 비어 있다")
    void brandProfile() throws Exception {
        info(brandChannel)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value(brandChannel.getId()))
                .andExpect(jsonPath("$.tab").value("BRAND"))
                .andExpect(jsonPath("$.name").value("글로우랩"))
                .andExpect(jsonPath("$.memberNo").value("BRD-" + brand.marketId()))
                .andExpect(jsonPath("$.memberId").value(brand.marketId()))
                .andExpect(jsonPath("$.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.writable").value(true))
                .andExpect(jsonPath("$.profile.managerName").value("김담당"))
                .andExpect(jsonPath("$.profile.managerContact").value("010-1111-2222"))
                .andExpect(jsonPath("$.profile.joinedAt").exists())
                .andExpect(jsonPath("$.profile.businessType").value(nullValue()))
                .andExpect(jsonPath("$.profile.businessEmail").value(nullValue()))
                .andExpect(jsonPath("$.profile.instagramUrl").value(nullValue()))
                .andExpect(jsonPath("$.profile.taxType").value(nullValue()));
    }

    @Test
    @DisplayName("인플루언서 채널 — 담당자 대신 사업자 여부 · 업무용 이메일이 온다. 과세 유형은 저장 값이 없어 null이다")
    void creatorProfile() throws Exception {
        info(creatorChannel)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tab").value("INFLUENCER"))
                .andExpect(jsonPath("$.name").value("뷰티_하윤"))
                .andExpect(jsonPath("$.memberNo").value("INF-" + creator.getId()))
                .andExpect(jsonPath("$.memberId").value(creator.getId()))
                .andExpect(jsonPath("$.profile.businessType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.profile.businessEmail").value("hayun-biz@showroomz.test"))
                .andExpect(jsonPath("$.profile.taxType").value(nullValue()))
                .andExpect(jsonPath("$.profile.joinedAt").exists())
                .andExpect(jsonPath("$.profile.managerName").value(nullValue()))
                .andExpect(jsonPath("$.profile.managerContact").value(nullValue()));
    }

    @Test
    @DisplayName("헤더의 회원 상태 · 쓰기 가능 여부는 목록과 같은 판정이다")
    void headerReflectsMemberStatus() throws Exception {
        setMarketStatus(brand, "WITHDRAWN");
        info(brandChannel).andExpect(jsonPath("$.memberStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.writable").value(false));

        setUserStatus(creator, "SUSPENDED");
        info(creatorChannel).andExpect(jsonPath("$.memberStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.writable").value(true));
    }

    // ------------------------------------------------------------------ ② 진행 중 — 계약

    @Test
    @DisplayName("계약 — 서명 진행중(서명 진행중 + 체결 처리 대기) · 체결이 각각 세어지고 종결 · 검토 단계는 빠진다")
    void contractCounts() throws Exception {
        seed(ContractStatus.SIGNING);
        seed(ContractStatus.SIGNING, s -> s.brandSignedAt(now.minusHours(5)).asOf(now.minusHours(4)));
        seed(ContractStatus.CONCLUSION_PENDING);
        seed(ContractStatus.CONCLUDED);
        seed(ContractStatus.CANCELED);
        seed(ContractStatus.EXPIRED);
        seed(ContractStatus.REVIEW_PENDING);
        seed(ContractStatus.DRAFT);

        info(brandChannel)
                .andExpect(jsonPath("$.progress.contractSigning").value(3))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1));
        info(creatorChannel)
                .andExpect(jsonPath("$.progress.contractSigning").value(3))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1));
    }

    @Test
    @DisplayName("계약 수는 그 회원 것만 센다 — 다른 브랜드 · 다른 인플루언서의 계약은 섞이지 않는다")
    void contractCountsAreScopedToMember() throws Exception {
        BrandFixture.Brand mood = brandWithChannel("mood@showroomz.test", "무드코스메틱");
        Creator minji = createCreator("민지의 쇼룸", "minji");
        MessageThread minjiChannel = channelOf(minji);
        seed(ContractStatus.SIGNING, s -> s.owner(mood));
        seed(ContractStatus.SIGNING, s -> s.counterparty(minji));
        seed(ContractStatus.CONCLUDED, s -> s.owner(mood).counterparty(minji));

        info(brandChannel).andExpect(jsonPath("$.progress.contractSigning").value(1))
                .andExpect(jsonPath("$.progress.contractConcluded").value(0));
        info(channelOf(mood.market())).andExpect(jsonPath("$.progress.contractSigning").value(1))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1));
        info(creatorChannel).andExpect(jsonPath("$.progress.contractSigning").value(1));
        info(minjiChannel).andExpect(jsonPath("$.progress.contractSigning").value(1))
                .andExpect(jsonPath("$.progress.contractConcluded").value(1));
    }

    // ------------------------------------------------------------------ ② 진행 중 — 공구 · 연결 · 미정산

    @Test
    @DisplayName("공구 — 진행중(판매 중인 상태)과 인플루언서의 종료(종료 + 정산완료)를 센다. 준비 · 중단은 빠진다")
    void groupBuyCounts() throws Exception {
        GroupBuy inProgress = concludedGroupBuy();
        GroupBuy suspensionScheduled = concludedGroupBuy();
        GroupBuy ended = concludedGroupBuy();
        GroupBuy settled = concludedGroupBuy();
        concludedGroupBuy();                                   // 준비중 그대로
        GroupBuy suspended = concludedGroupBuy();
        setStatus(inProgress, GroupBuyStatus.IN_PROGRESS);
        setStatus(suspensionScheduled, GroupBuyStatus.SUSPENSION_SCHEDULED);
        setStatus(ended, GroupBuyStatus.ENDED);
        setStatus(settled, GroupBuyStatus.SETTLED);
        setStatus(suspended, GroupBuyStatus.SUSPENDED);

        info(brandChannel)
                .andExpect(jsonPath("$.progress.groupBuyOngoing").value(2))
                .andExpect(jsonPath("$.progress.groupBuyEnded").value(nullValue()))
                .andExpect(jsonPath("$.progress.contractConcluded").value(6));
        info(creatorChannel)
                .andExpect(jsonPath("$.progress.groupBuyOngoing").value(2))
                .andExpect(jsonPath("$.progress.groupBuyEnded").value(2));
    }

    @Test
    @DisplayName("공구가 없으면 0이다 — 집계할 수 있는 「없음」은 null이 아니다")
    void zeroIsNotNull() throws Exception {
        info(brandChannel).andExpect(jsonPath("$.progress.contractSigning").value(0))
                .andExpect(jsonPath("$.progress.contractConcluded").value(0))
                .andExpect(jsonPath("$.progress.groupBuyOngoing").value(0));
        info(creatorChannel).andExpect(jsonPath("$.progress.groupBuyEnded").value(0));
    }

    @Test
    @DisplayName("연결 브랜드 수는 인플루언서에만 있고 연결됨만 센다 — 요청중 · 해제는 빠진다")
    void connectedBrandCount() throws Exception {
        info(creatorChannel).andExpect(jsonPath("$.progress.connectedBrandCount").value(1));

        BrandFixture.Brand mood = fixture.createBrand("mood@showroomz.test", "무드코스메틱");
        Connection connected = Connection.requestPair(mood.market(), creator);
        connected.markConnected();
        connections.save(connected);
        connections.save(Connection.requestPair(fixture.createBrand("pure@showroomz.test", "퓨어랩").market(), creator));
        Connection disconnected = Connection.requestPair(fixture.createBrand("daily@showroomz.test", "데일리랩").market(), creator);
        disconnected.markConnected();
        disconnected.markDisconnected();
        connections.save(disconnected);

        info(creatorChannel).andExpect(jsonPath("$.progress.connectedBrandCount").value(2));
        info(brandChannel).andExpect(jsonPath("$.progress.connectedBrandCount").value(nullValue()));
    }

    @Test
    @DisplayName("미정산 건수는 정산 모듈이 없어 null이다 — 0으로 그리면 「미정산 없음」이라는 거짓이 된다")
    void unsettledIsUnknown() throws Exception {
        concludedGroupBuy();

        info(brandChannel).andExpect(jsonPath("$.progress.unsettledCount").value(nullValue()));
        info(creatorChannel).andExpect(jsonPath("$.progress.unsettledCount").value(nullValue()));
    }

    // ------------------------------------------------------------------ ③ 열린 이슈 스레드

    @Test
    @DisplayName("열린 이슈 스레드 — 이슈가 열린 이슈 스레드와 이행 이견 스레드가 양측 모두에게 나오고, 닫힌 이슈 · 다른 쌍의 스레드는 빠진다")
    void openIssueThreads() throws Exception {
        GroupBuy issueGroupBuy = concludedGroupBuy();
        GroupBuy fulfillmentGroupBuy = concludedGroupBuy();
        GroupBuy closedGroupBuy = concludedGroupBuy();
        MessageThread openIssue = issueThread(issueGroupBuy, thread.getConnection(), false);
        MessageThread fulfillment = threadRepository.save(MessageThread.openForGroupBuy(
                thread.getConnection(), ThreadKind.GROUP_BUY_FULFILLMENT, fulfillmentGroupBuy.getId()));
        issueThread(closedGroupBuy, thread.getConnection(), true);

        // 다른 브랜드와 같은 인플루언서의 이슈 — 인플루언서에게는 보이고 글로우랩에게는 안 보인다.
        BrandFixture.Brand mood = fixture.createBrand("mood@showroomz.test", "무드코스메틱");
        Connection moodPair = Connection.requestPair(mood.market(), creator);
        moodPair.markConnected();
        connections.save(moodPair);
        GroupBuy moodGroupBuy = concludedGroupBuy(mood);
        MessageThread moodIssue = issueThread(moodGroupBuy, moodPair, false);

        info(brandChannel)
                .andExpect(jsonPath("$.openIssueThreads", hasSize(2)))
                .andExpect(jsonPath("$.openIssueThreads[*].threadId").value(containsInAnyOrder(
                        openIssue.getId().intValue(), fulfillment.getId().intValue())))
                .andExpect(jsonPath("$.openIssueThreads[?(@.threadId == " + openIssue.getId() + ")].kind").value("GROUP_BUY_ISSUE"))
                .andExpect(jsonPath("$.openIssueThreads[?(@.threadId == " + openIssue.getId() + ")].groupBuyId")
                        .value(issueGroupBuy.getId().intValue()))
                .andExpect(jsonPath("$.openIssueThreads[?(@.threadId == " + openIssue.getId() + ")].groupBuyTitle")
                        .value("겨울 리페어 크림 공구"))
                .andExpect(jsonPath("$.openIssueThreads[?(@.threadId == " + fulfillment.getId() + ")].kind")
                        .value("GROUP_BUY_FULFILLMENT"));

        info(creatorChannel)
                .andExpect(jsonPath("$.openIssueThreads", hasSize(3)))
                .andExpect(jsonPath("$.openIssueThreads[*].threadId").value(containsInAnyOrder(
                        openIssue.getId().intValue(), fulfillment.getId().intValue(), moodIssue.getId().intValue())));
    }

    @Test
    @DisplayName("열린 이슈 스레드가 없으면 빈 배열이다")
    void noOpenIssueThreads() throws Exception {
        info(brandChannel).andExpect(jsonPath("$.openIssueThreads", hasSize(0)));
        info(creatorChannel).andExpect(jsonPath("$.openIssueThreads", hasSize(0)));
    }

    // ------------------------------------------------------------------ 접근

    @Test
    @DisplayName("운영팀 채널이 아닌 스레드는 403, 없는 스레드는 404, 운영자가 아니면 403이다")
    void access() throws Exception {
        info(thread.getId()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("THREAD_ACCESS_DENIED"));
        GroupBuy groupBuy = concludedGroupBuy();
        MessageThread issue = issueThread(groupBuy, thread.getConnection(), false);
        info(issue.getId()).andExpect(status().isForbidden());
        info(999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("THREAD_NOT_FOUND"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(ADMIN_THREADS + brandChannel.getId() + "/info")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, brandToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 적재

    /** 체결 API로 공구를 만든다 — 체결 트랜잭션이 공구를 만드는 운영 경로를 그대로 탄다. */
    private GroupBuy concludedGroupBuy() throws Exception {
        return concludedGroupBuy(brand);
    }

    private GroupBuy concludedGroupBuy(BrandFixture.Brand owner) throws Exception {
        Contract c = seed(ContractStatus.CONCLUSION_PENDING, s -> s.owner(owner));
        attach(c, ContractDocumentType.SIGNED_PDF);
        attach(c, ContractDocumentType.AUDIT_TRAIL);
        conclude(c).andExpect(status().isOk());
        return groupBuys.findByContractId(c.getId()).orElseThrow();
    }

    private void setStatus(GroupBuy groupBuy, GroupBuyStatus status) {
        jdbc.update("UPDATE group_buy SET status = ? WHERE group_buy_id = ?", status.name(), groupBuy.getId());
    }

    private MessageThread issueThread(GroupBuy groupBuy, Connection pair, boolean closed) {
        MessageThread issueThread = threadRepository.save(
                MessageThread.openForGroupBuy(pair, ThreadKind.GROUP_BUY_ISSUE, groupBuy.getId()));
        GroupBuyIssue issue = GroupBuyIssue.open(groupBuy, GroupBuyActorType.SELLER, groupBuy.getMarket().getId(),
                GroupBuyIssueType.SETTLEMENT_AMOUNT, "정산 예정 금액에 이견이 있습니다.", issueThread.getId(), now);
        if (closed) {
            issue.close(now);
        }
        issues.save(issue);
        return issueThread;
    }
}
