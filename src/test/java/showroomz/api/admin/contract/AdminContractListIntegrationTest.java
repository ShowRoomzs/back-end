package showroomz.api.admin.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.support.BrandFixture;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시안 A1(목록 · 조치 큐 + 상태 탭 + 검색) · A2(빈 상태).
 *
 * <p>A1이 그린 10건을 그대로 적재하고 화면의 숫자 — 조치 큐 2·1·1·1, 탭 10·2·3·1·1·2, 「조치 필요 5건」 — 가
 * 서버에서 그대로 나오는지 본다. 탭 합(9)과 전체(10)가 다른 것은 버그가 아니라 사양이다(검토 반려는 전체 탭에만).
 */
@DisplayName("[통합] 어드민 계약 목록·요약 (A1·A2)")
class AdminContractListIntegrationTest extends AdminContractTestSupport {

    @Nested
    @DisplayName("A1 시안 데이터 10건")
    class SpecDataset {

        private final Map<String, Contract> rows = new LinkedHashMap<>();

        @BeforeEach
        void seedSpecRows() {
            BrandFixture.Brand pureNature = fixture.createBrand("purenature@showroomz.test", "퓨어네이처");
            BrandFixture.Brand dailyLab = fixture.createBrand("dailylab@showroomz.test", "데일리랩");
            BrandFixture.Brand mood = fixture.createBrand("mood@showroomz.test", "무드코스메틱");
            BrandFixture.Brand bella = fixture.createBrand("bella@showroomz.test", "벨라코스");
            BrandFixture.Brand cosmeticLab = fixture.createBrand("cosmeticlab@showroomz.test", "코스메틱랩");
            BrandFixture.Brand pureLab = fixture.createBrand("purelab@showroomz.test", "퓨어랩");
            Creator jimin = createCreator("글로우_지민", "jimin");
            Creator soyeon = createCreator("뷰티_소연", "soyeon");
            Creator raon = createCreator("뷰티_라온", "raon");
            Creator haneul = createCreator("코스메_하늘", "haneul");

            row(ContractStatus.REVIEW_PENDING, s -> s.title("겨울 리페어 크림 공구").counterparty(creator).items(1)
                    .startAt(spec("2026-09-20T10:00")).reviewRequestedAt(spec("2026-08-13T16:52")));
            row(ContractStatus.REVIEW_PENDING, s -> s.title("가을 앰플 신제품 공구").owner(pureNature).counterparty(jimin).items(2)
                    .startAt(spec("2026-09-01T10:00")).days(17).reviewRequestedAt(spec("2026-08-13T16:40")));
            row(ContractStatus.CONCLUSION_PENDING, s -> s.title("데일리 토너 공구").owner(dailyLab).counterparty(jimin).items(2)
                    .startAt(spec("2026-09-05T10:00")).reviewRequestedAt(spec("2026-08-12T10:20"))
                    .sentAt(spec("2026-08-12T15:00")).deadlineAt(spec("2026-08-19T23:55")));
            row(ContractStatus.SIGNING, s -> s.title("여름 수분 세럼 공구").owner(mood).counterparty(soyeon).items(2)
                    .startAt(spec("2026-08-24T10:00")).reviewRequestedAt(spec("2026-08-13T14:50"))
                    .sentAt(spec("2026-08-13T16:40")).deadlineAt(spec("2026-08-21T23:55"))
                    .brandSignedAt(spec("2026-08-14T09:12")).asOf(spec("2026-08-14T09:30")));
            row(ContractStatus.SIGNING, s -> s.title("수분 토너 리뉴얼 공구").owner(bella).counterparty(raon).items(2)
                    .startAt(spec("2026-09-28T10:00")).reviewRequestedAt(spec("2026-08-09T11:00"))
                    .sentAt(spec("2026-08-10T10:00")).deadlineAt(spec("2026-08-25T23:55")));
            row(ContractStatus.REVIEW_REJECTED, s -> s.title("앰플 리필 공구").counterparty(creator).items(1)
                    .startAt(spec("2026-09-25T10:00")).reviewRequestedAt(spec("2026-08-11T15:30")));
            row(ContractStatus.CONCLUDED, s -> s.title("글로우 크림 앵콜 공구").counterparty(jimin).items(1)
                    .startAt(spec("2026-08-14T10:00")).reviewRequestedAt(spec("2026-08-05T13:10"))
                    .sentAt(spec("2026-08-06T10:00")).deadlineAt(spec("2026-08-12T23:55")));
            row(ContractStatus.DECLINED, s -> s.title("봄 클렌저 공구").owner(cosmeticLab).counterparty(haneul).items(1)
                    .startAt(spec("2026-08-01T10:00")).reviewRequestedAt(spec("2026-07-22T10:15"))
                    .sentAt(spec("2026-07-23T10:00")).deadlineAt(spec("2026-07-28T23:55")));
            // C5의 계약 — 서명 기한이 지났지만 아무도 닫지 않았다. 상태는 여전히 서명 진행중이다.
            row(ContractStatus.SIGNING, s -> s.title("수분 크림 겨울 공구").owner(bella).counterparty(raon).items(1)
                    .startAt(spec("2026-07-25T10:00")).reviewRequestedAt(spec("2026-07-15T14:20"))
                    .sentAt(spec("2026-07-18T10:30")).deadlineAt(spec("2026-07-22T23:55"))
                    .brandSignedAt(spec("2026-07-19T14:02")).asOf(spec("2026-07-20T09:00")));
            row(ContractStatus.CANCELED, s -> s.title("클렌징 오일 여름 공구").owner(pureLab).counterparty(soyeon).items(1)
                    .startAt(spec("2026-07-20T10:00")).reviewRequestedAt(spec("2026-07-10T11:45"))
                    .sentAt(spec("2026-07-11T10:00")).deadlineAt(spec("2026-07-18T23:55")));

            // B3 — 뷰티_소연의 [서명 안내 다시 받기]. 처리된 과거 요청은 큐에 남지 않는다.
            resendRequested(rows.get("여름 수분 세럼 공구"), ContractActorType.CREATOR, spec("2026-08-14T08:50"));
            resendRequested(rows.get("수분 토너 리뉴얼 공구"), ContractActorType.SELLER, spec("2026-08-11T09:00"));
            transactionTemplate.execute(tx -> resends.handleAll(
                    rows.get("수분 토너 리뉴얼 공구").getId(), admin.getId(), spec("2026-08-11T10:00")));

            // 작성중은 운영자에게 도착하지 않는다 — 11번째 행이지만 어디에도 세지 않는다.
            seed(ContractStatus.DRAFT, s -> s.title("작성 중인 공구"));
        }

        private void row(ContractStatus status, java.util.function.Consumer<Seed> shape) {
            Contract contract = seed(status, s -> {
                shape.accept(s);
                s.createdAt(now.minusMinutes(10 - rows.size()));
            });
            rows.put(contract.getTitle(), contract);
        }

        @Test
        @DisplayName("요약: 조치 큐 2·1·1·1, 탭 10·2·3·1·1·2, 조치 필요 5건 — 시안 A1의 숫자 그대로다")
        void summaryMatchesSpecNumbers() throws Exception {
            summary().andExpect(status().isOk())
                    .andExpect(jsonPath("$.queues.REVIEW").value(2))
                    .andExpect(jsonPath("$.queues.CONCLUSION").value(1))
                    .andExpect(jsonPath("$.queues.EXPIRY").value(1))
                    .andExpect(jsonPath("$.queues.RESEND").value(1))
                    .andExpect(jsonPath("$.actionRequiredCount").value(5))
                    .andExpect(jsonPath("$.tabCounts.ALL").value(10))
                    .andExpect(jsonPath("$.tabCounts.REVIEW_PENDING").value(2))
                    .andExpect(jsonPath("$.tabCounts.SIGNING").value(3))
                    .andExpect(jsonPath("$.tabCounts.CONCLUSION_PENDING").value(1))
                    .andExpect(jsonPath("$.tabCounts.CONCLUDED").value(1))
                    .andExpect(jsonPath("$.tabCounts.CLOSED").value(2))
                    // 검토 반려는 탭이 없다 — 전체에만 잡혀 탭 합(9)이 전체(10)와 다르다.
                    .andExpect(jsonPath("$.tabCounts", not(hasKey("REVIEW_REJECTED"))))
                    .andExpect(jsonPath("$.tabCounts", not(hasKey("DRAFT"))));
        }

        @Test
        @DisplayName("기본 목록: 작성중 제외 10건 · 검토 요청 오래된순 · 브랜드와 인플루언서 2열 · 행에 조치 권한 없음")
        void defaultListIsOldestReviewRequestFirst() throws Exception {
            list().andExpect(status().isOk())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(10))
                    .andExpect(jsonPath("$.pageInfo.limit").value(20))
                    .andExpect(jsonPath("$.content[*].title", contains(
                            "클렌징 오일 여름 공구", "수분 크림 겨울 공구", "봄 클렌저 공구", "글로우 크림 앵콜 공구",
                            "수분 토너 리뉴얼 공구", "앰플 리필 공구", "데일리 토너 공구", "여름 수분 세럼 공구",
                            "가을 앰플 신제품 공구", "겨울 리페어 크림 공구")))
                    .andExpect(jsonPath("$.content[*].title", not(hasItem("작성 중인 공구"))))
                    .andExpect(jsonPath("$.content[*].permissions").isEmpty())
                    .andExpect(jsonPath("$.content[*].warningFlags").isEmpty());

            Contract winter = rows.get("겨울 리페어 크림 공구");
            list("keyword", winter.getContractNumber())
                    .andExpect(jsonPath("$.content[0].contractId").value(winter.getId()))
                    .andExpect(jsonPath("$.content[0].contractNumber").value(winter.getContractNumber()))
                    .andExpect(jsonPath("$.content[0].brandName").value("글로우랩"))
                    .andExpect(jsonPath("$.content[0].creatorName").value("뷰티_하윤"))
                    .andExpect(jsonPath("$.content[0].itemCount").value(1))
                    .andExpect(jsonPath("$.content[0].startAt").exists())
                    .andExpect(jsonPath("$.content[0].endAt").exists())
                    .andExpect(jsonPath("$.content[0].reviewRequestedAt").exists())
                    .andExpect(jsonPath("$.content[0].statusLabel").value("검토 대기"));

            list("keyword", "가을 앰플")
                    .andExpect(jsonPath("$.content[0].brandName").value("퓨어네이처"))
                    .andExpect(jsonPath("$.content[0].creatorName").value("글로우_지민"))
                    .andExpect(jsonPath("$.content[0].itemCount").value(2));
        }

        @Test
        @DisplayName("상태 배지 색은 세 서피스 공통 — 검토 대기·서명 진행중·체결 처리 대기=정보, 반려=경고, 체결=성공, 거절=위험, 만료·취소=중립")
        void statusTonesAreSharedAcrossSurfaces() throws Exception {
            Map<String, String[]> expected = Map.of(
                    "겨울 리페어 크림 공구", new String[]{"검토 대기", "INFO"},
                    "여름 수분 세럼 공구", new String[]{"서명 진행중", "INFO"},
                    "데일리 토너 공구", new String[]{"체결 처리 대기", "INFO"},
                    "앰플 리필 공구", new String[]{"검토 반려", "WARNING"},
                    "글로우 크림 앵콜 공구", new String[]{"체결완료", "SUCCESS"},
                    "봄 클렌저 공구", new String[]{"거절", "DANGER"},
                    "클렌징 오일 여름 공구", new String[]{"취소", "NEUTRAL"});
            for (var entry : expected.entrySet()) {
                list("keyword", rows.get(entry.getKey()).getContractNumber())
                        .andExpect(jsonPath("$.content[0].statusLabel").value(entry.getValue()[0]))
                        .andExpect(jsonPath("$.content[0].statusTone").value(entry.getValue()[1]));
            }
        }

        @Test
        @DisplayName("탭 6종은 각자의 상태만 담고, 검토 반려는 전체 탭에만 나온다")
        void tabsFilterByStatus() throws Exception {
            list("tab", "ALL").andExpect(jsonPath("$.content[*].status", hasItem("REVIEW_REJECTED")));
            list("tab", "REVIEW_PENDING").andExpect(jsonPath("$.content[*].title",
                    containsInAnyOrder("겨울 리페어 크림 공구", "가을 앰플 신제품 공구")));
            list("tab", "SIGNING").andExpect(jsonPath("$.content[*].title",
                    containsInAnyOrder("여름 수분 세럼 공구", "수분 토너 리뉴얼 공구", "수분 크림 겨울 공구")));
            list("tab", "CONCLUSION_PENDING").andExpect(jsonPath("$.content[*].title", contains("데일리 토너 공구")));
            list("tab", "CONCLUDED").andExpect(jsonPath("$.content[*].title", contains("글로우 크림 앵콜 공구")));
            list("tab", "CLOSED").andExpect(jsonPath("$.content[*].status", containsInAnyOrder("DECLINED", "CANCELED")));

            // 탭에 없는 두 상태는 탭 값으로 받지 않는다.
            list("tab", "REVIEW_REJECTED").andExpect(status().isBadRequest());
            list("tab", "DRAFT").andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("조치 큐를 누르면 목록 필터가 걸린다 — 큐 4종 각각의 대상")
        void queuesFilterActionableContracts() throws Exception {
            list("queue", "REVIEW").andExpect(jsonPath("$.content[*].title",
                    contains("가을 앰플 신제품 공구", "겨울 리페어 크림 공구")));
            list("queue", "CONCLUSION").andExpect(jsonPath("$.content[*].title", contains("데일리 토너 공구")));
            // 「만료 확인」은 상태가 아니라 조건이다 — 기한 경과한 서명 진행중만. 기한 전 서명 진행중은 빠진다.
            list("queue", "EXPIRY").andExpect(jsonPath("$.content[*].title", contains("수분 크림 겨울 공구")))
                    .andExpect(jsonPath("$.content[0].status").value("SIGNING"));
            // 처리된 요청만 있는 계약은 큐에 없다.
            list("queue", "RESEND").andExpect(jsonPath("$.content[*].title", contains("여름 수분 세럼 공구")));
        }

        @Test
        @DisplayName("큐를 고르면 탭·정렬보다 큐가 우선한다")
        void queueOverridesTabAndSort() throws Exception {
            list("queue", "REVIEW", "tab", "CONCLUDED", "sort", "CREATED_DESC")
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                    .andExpect(jsonPath("$.content[*].title", contains("가을 앰플 신제품 공구", "겨울 리페어 크림 공구")));
        }

        @Test
        @DisplayName("A2: 검색은 공구명·브랜드명·쇼룸명·계약번호 네 가지로 걸린다")
        void searchesFourFields() throws Exception {
            list("keyword", "리페어").andExpect(jsonPath("$.content[*].title", contains("겨울 리페어 크림 공구")));
            list("keyword", "벨라코스").andExpect(jsonPath("$.content[*].title",
                    containsInAnyOrder("수분 토너 리뉴얼 공구", "수분 크림 겨울 공구")));
            list("keyword", "뷰티_소연").andExpect(jsonPath("$.content[*].title",
                    containsInAnyOrder("여름 수분 세럼 공구", "클렌징 오일 여름 공구")));
            list("keyword", rows.get("데일리 토너 공구").getContractNumber())
                    .andExpect(jsonPath("$.content[*].title", contains("데일리 토너 공구")));
            // 검색과 탭은 함께 걸린다.
            list("keyword", "글로우랩", "tab", "REVIEW_PENDING")
                    .andExpect(jsonPath("$.content[*].title", contains("겨울 리페어 크림 공구")));
        }

        @Test
        @DisplayName("A2: 검색 결과가 비어도 조치 큐는 검색과 무관하게 그대로 있다")
        void emptySearchKeepsQueues() throws Exception {
            list("keyword", "존재하지 않는 공구명")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.pageInfo.totalResults").value(0));

            summary().andExpect(jsonPath("$.actionRequiredCount").value(5))
                    .andExpect(jsonPath("$.tabCounts.ALL").value(10));
        }

        @Test
        @DisplayName("정렬 3종 — 최근 생성순 · 공구 시작일순")
        void sortsByCreatedAndStart() throws Exception {
            list("sort", "START_AT_ASC").andExpect(jsonPath("$.content[0].title").value("클렌징 오일 여름 공구"))
                    .andExpect(jsonPath("$.content[9].title").value("수분 토너 리뉴얼 공구"));
            // 적재 순서의 역순 — 마지막으로 만든 「클렌징 오일 여름 공구」가 맨 위다.
            list("sort", "CREATED_DESC").andExpect(jsonPath("$.content[0].title").value("클렌징 오일 여름 공구"))
                    .andExpect(jsonPath("$.content[9].title").value("겨울 리페어 크림 공구"));
            list("sort", "UNKNOWN").andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("큐별 정렬: 체결 처리 대기는 기준 시각 오래된순, 만료 확인은 기한 오래된순, 재발송은 가장 오래된 미처리 요청순")
    void queuesHaveTheirOwnOrdering() throws Exception {
        Contract laterAsOf = seed(ContractStatus.CONCLUSION_PENDING, s -> s.title("나중 확인").asOf(now.minusHours(1)));
        Contract earlierAsOf = seed(ContractStatus.CONCLUSION_PENDING, s -> s.title("먼저 확인").asOf(now.minusHours(5)));
        list("queue", "CONCLUSION").andExpect(jsonPath("$.content[*].contractId",
                contains(earlierAsOf.getId().intValue(), laterAsOf.getId().intValue())));

        Contract recentDeadline = seed(ContractStatus.SIGNING, s -> s.title("어제 마감").sentAt(now.minusDays(10)).deadlineAt(now.minusDays(1)));
        Contract oldDeadline = seed(ContractStatus.SIGNING, s -> s.title("열흘 전 마감").sentAt(now.minusDays(20)).deadlineAt(now.minusDays(10)));
        list("queue", "EXPIRY").andExpect(jsonPath("$.content[*].contractId",
                contains(oldDeadline.getId().intValue(), recentDeadline.getId().intValue())));

        Contract newer = seed(ContractStatus.SIGNING, s -> s.title("방금 요청"));
        Contract older = seed(ContractStatus.SIGNING, s -> s.title("오래된 요청"));
        resendRequested(newer, ContractActorType.CREATOR, now.minusMinutes(5));
        resendRequested(older, ContractActorType.SELLER, now.minusHours(6));
        // 브랜드·인플루언서가 각각 요청해도 계약 단위로 한 건이다.
        resendRequested(older, ContractActorType.CREATOR, now.minusHours(1));
        list("queue", "RESEND").andExpect(jsonPath("$.pageInfo.totalResults").value(2))
                .andExpect(jsonPath("$.content[*].contractId", contains(older.getId().intValue(), newer.getId().intValue())));
        summary().andExpect(jsonPath("$.queues.RESEND").value(2));
    }

    @Test
    @DisplayName("페이지는 1부터 · 20건씩 또는 50건씩만 받는다")
    void pagesByTwentyOrFifty() throws Exception {
        for (int i = 0; i < 21; i++) {
            int minutes = i;
            seed(ContractStatus.REVIEW_PENDING, s -> s.title("공구 " + minutes).reviewRequestedAt(now.minusHours(30).plusMinutes(minutes)));
        }
        list("page", "2").andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.currentPage").value(2))
                .andExpect(jsonPath("$.pageInfo.totalResults").value(21))
                .andExpect(jsonPath("$.content[*].title", contains("공구 20")));
        list("size", "50").andExpect(status().isOk())
                .andExpect(jsonPath("$.pageInfo.limit").value(50))
                .andExpect(jsonPath("$.content.length()").value(21));
        list("size", "30").andExpect(status().isBadRequest());
        list("size", "10").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("목록·요약은 운영자만 본다 — 브랜드·인플루언서 토큰은 403, 토큰 없으면 401")
    void listIsForOperatorsOnly() throws Exception {
        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, brandToken)).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/summary").header(HttpHeaders.AUTHORIZATION, creatorToken)).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
}
