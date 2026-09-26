package showroomz.api.seller.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyEventType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.support.BrandFixture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("[통합] 파트너센터 공구 조회 — 생성 · 목록 · 요약 · 상세")
class SellerGroupBuyQueryIntegrationTest extends GroupBuyTestSupport {

    // ── 생성 — 체결 트랜잭션 ──────────────────────────────────────────────

    @Test
    @DisplayName("공구는 체결에서 생긴다 — 준비중 · 기간 복사 · GB 번호 · 시스템 이력 · 계약 연결 · 상품 준비중 동기화")
    void creationFromConcludedContract() {
        GroupBuy groupBuy = seedPreparing();

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.PREPARING);
        String datePart = LocalDate.now().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(groupBuy.getGroupBuyNumber()).isEqualTo("GB-" + datePart + "-001");

        Long contractId = inTransaction(() -> reload(groupBuy.getId()).getContract().getId());
        assertThat(contractRepository.findById(contractId).orElseThrow().getGroupBuyId()).isEqualTo(groupBuy.getId());
        assertThat(contractHistoryRepository.findByContractIdOrderByOccurredAtAscIdAsc(contractId).getLast().getEventType())
                .isEqualTo(ContractEventType.GROUP_BUY_CREATED);

        var history = groupBuyHistoryRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId());
        assertThat(history).singleElement().satisfies(entry -> {
            assertThat(entry.getEventType()).isEqualTo(GroupBuyEventType.CREATED);
            assertThat(entry.getActorType()).isEqualTo(GroupBuyActorType.SYSTEM);
        });

        // 상품은 연결됐지만 아직 팔 수 없다 — 장바구니는 진행중만 판다.
        assertThat(productStatus(cream)).isEqualTo(ProductGroupBuyStatus.PREPARING);
        assertThat(productStatus(serum)).isEqualTo(ProductGroupBuyStatus.PREPARING);
    }

    @Test
    @DisplayName("계약 1건 = 공구 1건 — 이미 공구가 있는 계약으로 다시 만들면 409이고 아무것도 남지 않는다")
    void secondCreationIsRejected() throws Exception {
        GroupBuy groupBuy = seedPreparing();
        Long contractId = inTransaction(() -> reload(groupBuy.getId()).getContract().getId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx ->
                        groupBuyFactory.createFromConcludedContract(
                                contractRepository.findById(contractId).orElseThrow(), LocalDateTime.now())))
                .hasMessageContaining("이미 공구가 생성된 계약");
        assertThat(groupBuyRepository.count()).isEqualTo(1);
    }

    // ── A1 목록 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("탭은 필터일 뿐 배지는 개별 값이다 — 진행중 탭에 중단 예정이 경고색 그대로 들어온다")
    void inProgressTabCarriesSuspensionScheduledAsItself() throws Exception {
        GroupBuy selling = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuy noticed = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(noticed.getId(), LocalDateTime.now().plusDays(2).withNano(0));
        seedIn(GroupBuyStatus.PREPARING);
        seedIn(GroupBuyStatus.SETTLED);

        list("tab=IN_PROGRESS").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].status").value(containsInAnyOrder("IN_PROGRESS", "SUSPENSION_SCHEDULED")))
                .andExpect(jsonPath("$.content[?(@.groupBuyId == %d)].statusTone".formatted(noticed.getId()))
                        .value(contains("WARNING")))
                .andExpect(jsonPath("$.content[?(@.groupBuyId == %d)].remark.code".formatted(noticed.getId()))
                        .value(contains("ADMIN_SUSPENSION_NOTICED")))
                .andExpect(jsonPath("$.content[?(@.groupBuyId == %d)].remark.appealDeadlineAt".formatted(noticed.getId()))
                        .exists())
                // 예외가 없는 행은 비고가 비어 있다 — 모든 행에 뭔가 적혀 있으면 예외가 눈에 띄지 않는다.
                .andExpect(jsonPath("$.content[?(@.groupBuyId == %d)].remark".formatted(selling.getId()))
                        .value(contains((Object) null)));

        list("tab=ENDED").andExpect(jsonPath("$.content[*].status").value(contains("SETTLED")));

        summary().andExpect(status().isOk())
                .andExpect(jsonPath("$.tabCounts.ALL").value(4))
                .andExpect(jsonPath("$.tabCounts.PREPARING").value(1))
                .andExpect(jsonPath("$.tabCounts.READY").value(0))
                .andExpect(jsonPath("$.tabCounts.IN_PROGRESS").value(2))
                .andExpect(jsonPath("$.tabCounts.ENDED").value(1))
                .andExpect(jsonPath("$.tabCounts.SUSPENDED").value(0));
    }

    @Test
    @DisplayName("비고는 하나만 — 직권 중단 예정이 중단 요청 검토·연장 대기보다 먼저다")
    void remarkPriority() throws Exception {
        GroupBuy reviewing = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPendingRequest(reviewing.getId(), ChangeRequestType.SUSPEND, GroupBuyActorType.CREATOR, "CREATOR_REASON");
        GroupBuy earlyClose = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPendingRequest(earlyClose.getId(), ChangeRequestType.EARLY_CLOSE, GroupBuyActorType.SELLER, "STOCK_OUT");

        list(null)
                .andExpect(jsonPath("$.content[?(@.groupBuyId == %d)].remark.code".formatted(reviewing.getId()))
                        .value(contains("SUSPENSION_REQUEST_REVIEWING")))
                .andExpect(jsonPath("$.content[?(@.groupBuyId == %d)].remark.code".formatted(earlyClose.getId()))
                        .value(contains("EARLY_CLOSE_REQUEST_REVIEWING")));
    }

    @Test
    @DisplayName("검색은 공구명 · 인플루언서명 · 공구번호 — 정렬 기본은 시작일 빠른순이다")
    void searchAndSort() throws Exception {
        Creator other = createCreator("데일리_수아", "sua");
        LocalDateTime base = LocalDateTime.now().plusDays(20).withNano(0);
        GroupBuy later = seed(brand, creator, "가을 앰플 공구", base.plusDays(5), base.plusDays(12));
        GroupBuy sooner = seed(brand, other, "겨울 크림 공구", base, base.plusDays(7));

        list(null).andExpect(jsonPath("$.content[*].groupBuyId").value(contains(
                sooner.getId().intValue(), later.getId().intValue())));
        list("sort=CREATED_DESC").andExpect(jsonPath("$.content[*].groupBuyId").value(contains(
                sooner.getId().intValue(), later.getId().intValue())));

        list("keyword=앰플").andExpect(jsonPath("$.content[*].groupBuyId").value(contains(later.getId().intValue())));
        list("keyword=수아").andExpect(jsonPath("$.content[*].groupBuyId").value(contains(sooner.getId().intValue())));
        list("keyword=" + later.getGroupBuyNumber())
                .andExpect(jsonPath("$.content[*].groupBuyId").value(contains(later.getId().intValue())))
                .andExpect(jsonPath("$.content[0].title").value("가을 앰플 공구"))
                .andExpect(jsonPath("$.content[0].creatorName").value("글로우_지민"))
                .andExpect(jsonPath("$.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.content[0].postStatus").value("NOT_WRITTEN"))
                .andExpect(jsonPath("$.content[0].postStatusLabel").value("미작성"));

        // 빈 검색 결과와 빈 목록은 같은 응답이다 — A2/A3 구분은 summary의 ALL로 한다.
        list("keyword=없는공구").andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("남의 브랜드 공구는 목록에 없고 상세·실행은 403 — 없는 공구는 404")
    void isolatesBrands() throws Exception {
        BrandFixture.Brand otherBrand = fixture.createBrand("other@showroomz.test", "아더랩");
        GroupBuy theirs = seed(otherBrand, createCreator("아더_쇼룸", "other"), "아더 공구",
                LocalDateTime.now().plusDays(10).withNano(0), LocalDateTime.now().plusDays(17).withNano(0));
        seedPreparing();

        list(null).andExpect(jsonPath("$.content.length()").value(1));
        detail(theirs.getId()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_OWNED_BY_SELLER"));
        action(theirs.getId(), "stock-confirmation", null).andExpect(status().isForbidden());
        detail(999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_BUY_NOT_FOUND"));
    }

    // ── A3 GNB 배지 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GNB 배지는 브랜드가 끌 수 있는 것만 — 물량 확인 대기 · 소명 가능 · 이행 확인 대기")
    void actionRequiredCountsOnlyBrandTurns() throws Exception {
        seedPreparing();                                                         // ① 물량 확인 대기
        GroupBuy confirmed = seedPreparing();
        confirmStockDirectly(confirmed.getId());                                 // 확인 끝 — 제외
        GroupBuy rejectedPost = seedPreparing();
        confirmStockDirectly(rejectedPost.getId());
        seedPost(rejectedPost.getId(), GroupBuyPostReviewStatus.REJECTED, false); // 반려는 인플루언서 몫 — 제외
        GroupBuy noticed = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(noticed.getId(), LocalDateTime.now().plusDays(2).withNano(0)); // ② 소명 가능
        seedIn(GroupBuyStatus.ENDED);                                            // ③ 이행 확인 대기
        GroupBuy waiting = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPendingRequest(waiting.getId(), ChangeRequestType.EARLY_CLOSE, GroupBuyActorType.SELLER, "STOCK_OUT"); // 대기 — 제외

        summary().andExpect(jsonPath("$.actionRequiredCount").value(3));
    }

    // ── B1 상세 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B1 준비중 상세 — 계약 조건은 참조 · 게이트 3개 · 표준 표기 · KPI 없음 · 내 차례 버튼 하나")
    void preparingDetail() throws Exception {
        GroupBuy groupBuy = seedPreparing();

        detail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBuy.groupBuyNumber").value(groupBuy.getGroupBuyNumber()))
                .andExpect(jsonPath("$.groupBuy.title").value("글로우 크림 앵콜 공구"))
                .andExpect(jsonPath("$.groupBuy.status").value("PREPARING"))
                .andExpect(jsonPath("$.groupBuy.statusLabel").value("준비중"))
                .andExpect(jsonPath("$.groupBuy.statusTone").value("NEUTRAL"))
                .andExpect(jsonPath("$.timeline.totalDays").value(8))
                .andExpect(jsonPath("$.timeline.elapsedDays").value(0))
                .andExpect(jsonPath("$.timeline.daysUntilStart").value(10))
                .andExpect(jsonPath("$.timeline.startOverdue").value(false))
                .andExpect(jsonPath("$.counterparty.name").value("글로우_지민"))
                .andExpect(jsonPath("$.counterparty.pairThreadId").value(pairThreadId))
                .andExpect(jsonPath("$.contract.contractNumber").exists())
                // 「리워드율 12% · 예상 리워드 3,264원」 = 27,200 × 12% 절사 — 계약과 같은 계산기다.
                .andExpect(jsonPath("$.items[0].groupBuyPrice").value(27_200))
                .andExpect(jsonPath("$.items[0].expectedUnitReward").value(3_264))
                .andExpect(jsonPath("$.items[0].minQuantity").value(300))
                .andExpect(jsonPath("$.fixedFee.displayText")
                        .value("고정 지급비 300,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급"))
                .andExpect(jsonPath("$.fixedFee.paidAt").doesNotExist())
                .andExpect(jsonPath("$.contentDuty.story").value(3))
                .andExpect(jsonPath("$.readiness.gates[*].key")
                        .value(contains("STOCK_CONFIRMED", "POST_SUBMITTED", "OPEN_APPROVED")))
                .andExpect(jsonPath("$.readiness.gates[*].state").value(contains("ACTION_REQUIRED", "WAITING", "WAITING")))
                .andExpect(jsonPath("$.post.status").value("NOT_WRITTEN"))
                .andExpect(jsonPath("$.sales").doesNotExist())
                .andExpect(jsonPath("$.orderClosure").doesNotExist())
                .andExpect(jsonPath("$.extension.status").doesNotExist())
                .andExpect(jsonPath("$.extension.maxDays").value(22))
                .andExpect(jsonPath("$.closure").doesNotExist())
                .andExpect(jsonPath("$.afterEnd").doesNotExist())
                .andExpect(jsonPath("$.history[*].eventType").value(contains("CREATED")))
                .andExpect(jsonPath("$.history[0].actorDisplayName").doesNotExist());

        assertPermissions(groupBuy.getId(), "canConfirmStock", "canOpenPairThread");
    }

    @Test
    @DisplayName("버튼 판정은 상태 × 요청 × 통지 × 게시물로 갈린다 — 원칙으로 집행한다")
    void permissionsFollowFacts() throws Exception {
        assertPermissions(seedIn(GroupBuyStatus.READY).getId(), "canRequestSuspension", "canOpenPairThread");
        assertPermissions(seedIn(GroupBuyStatus.IN_PROGRESS).getId(),
                "canRequestExtension", "canRequestEarlyClose", "canRequestSuspension", "canOpenPairThread");

        // 검토 중 요청(요청자 무관) — 추가 요청 전부 불가(§29-6).
        GroupBuy reviewing = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPendingRequest(reviewing.getId(), ChangeRequestType.SUSPEND, GroupBuyActorType.CREATOR, "CREATOR_REASON");
        assertPermissions(reviewing.getId(), "canOpenPairThread");

        // 게시물 숨김 중 — B4j 「숨김이 풀린 뒤에 하세요」.
        GroupBuy hidden = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(hidden.getId(), GroupBuyPostReviewStatus.APPROVED, true);
        assertPermissions(hidden.getId(), "canOpenPairThread");

        // 중단 예정 — 액션은 소명뿐(B4i).
        GroupBuy noticed = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedNotice(noticed.getId(), LocalDateTime.now().plusDays(2).withNano(0));
        assertPermissions(noticed.getId(), "canSubmitAppeal", "canOpenPairThread");

        assertPermissions(seedIn(GroupBuyStatus.ENDED).getId(), "canOpenIssue", "canCheckFulfillment", "canOpenPairThread");
        assertPermissions(seedIn(GroupBuyStatus.SETTLED).getId(), "canOpenPairThread");
    }

    @Test
    @DisplayName("진행중 상세 — 게시물 노출중 · 게이트 없음 · 판매 모듈이 없으니 KPI는 0이 아니라 null")
    void inProgressDetail() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedPost(groupBuy.getId(), GroupBuyPostReviewStatus.APPROVED, false);

        detail(groupBuy.getId())
                .andExpect(jsonPath("$.groupBuy.statusTone").value("SUCCESS"))
                .andExpect(jsonPath("$.readiness").doesNotExist())
                .andExpect(jsonPath("$.post.status").value("EXPOSED"))
                .andExpect(jsonPath("$.post.title").value("글로우 크림 앵콜 공구 오픈"))
                .andExpect(jsonPath("$.post.content").value("여름 한정 앵콜 공구 — 크림·세럼 세트"))
                .andExpect(jsonPath("$.timeline.elapsedDays").value(4))
                .andExpect(jsonPath("$.timeline.daysUntilEnd").value(4))
                .andExpect(jsonPath("$.sales").doesNotExist())
                .andExpect(jsonPath("$.orderClosure").doesNotExist());
    }

    @Test
    @DisplayName("종료 상세 — 종결 정보 · 이행 확인 블록 · 정산 감시 기준 · 게시물 종료")
    void endedDetail() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);

        detail(groupBuy.getId())
                .andExpect(jsonPath("$.groupBuy.statusTone").value("INFO"))
                .andExpect(jsonPath("$.groupBuy.closeType").value("COMPLETED"))
                .andExpect(jsonPath("$.post.status").value("NOT_WRITTEN"))
                .andExpect(jsonPath("$.closure.closeType").value("COMPLETED"))
                .andExpect(jsonPath("$.closure.source").doesNotExist())
                .andExpect(jsonPath("$.afterEnd.fulfillment.mine").doesNotExist())
                .andExpect(jsonPath("$.afterEnd.fulfillment.dueAt").exists())
                .andExpect(jsonPath("$.afterEnd.fulfillment.onHold").value(false))
                .andExpect(jsonPath("$.afterEnd.settlementWatchAt").exists())
                // 종료 화면에 KPI를 두지 않는다 — 잠정치가 지급액으로 오해된다(§30-4).
                .andExpect(jsonPath("$.sales").doesNotExist());
    }

    // ------------------------------------------------------------------ 헬퍼

    /** 나열한 권한만 true이고 나머지는 false여야 한다 — 「무엇이 꺼져 있는가」가 판정의 절반이다. */
    private void assertPermissions(long groupBuyId, String... allowed) throws Exception {
        ResultActions result = detail(groupBuyId).andExpect(status().isOk());
        List<String> all = List.of("canConfirmStock", "canRequestExtension", "canRequestEarlyClose",
                "canRequestSuspension", "canSubmitAppeal", "canOpenIssue", "canCheckFulfillment", "canOpenPairThread");
        List<String> expected = List.of(allowed);
        for (String permission : all) {
            result.andExpect(jsonPath("$.permissions." + permission).value(expected.contains(permission)));
        }
    }
}
