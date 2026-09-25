package showroomz.api.admin.groupbuy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import showroomz.api.seller.groupbuy.service.GroupBuyAppealAttachmentStorage;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyAppealAttachment;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.repository.GroupBuyAppealAttachmentRepository;
import showroomz.domain.groupbuy.repository.GroupBuyIssueRepository;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementGateway;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.FulfillmentResult;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyIssueStatus;
import showroomz.domain.groupbuy.type.GroupBuyIssueType;
import showroomz.domain.groupbuy.type.GroupBuyStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 판매·정산·이슈 스레드 포트가 실제 값을 돌려줄 때 운영자 API가 지키는 계약. */
@DisplayName("[통합] 어드민 공구 관리 외부 포트 계약")
class AdminGroupBuyPortIntegrationTest extends AdminGroupBuyTestSupport {

    @MockitoBean GroupBuySalesReader salesReader;
    @MockitoBean GroupBuySettlementGateway settlementGateway;
    @MockitoBean GroupBuyThreadGateway threadGateway;
    @MockitoBean GroupBuyAppealAttachmentStorage appealStorage;
    @Autowired GroupBuyIssueRepository issueRepository;
    @Autowired GroupBuyAppealAttachmentRepository appealAttachmentRepository;

    @Test
    @DisplayName("중단 요청 판단 근거는 요청 시점 주문과 현재 주문의 차이를 계산하고 1:1 문의를 상품 문의와 합친다")
    void suspensionBasisUsesSalesSnapshotAndInquiries() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyChangeRequest request = seedPendingRequest(groupBuy.getId(), ChangeRequestType.SUSPEND,
                GroupBuyActorType.CREATOR, "PRODUCT_DEFECT");
        jdbc.update("UPDATE group_buy_change_request SET sales_order_count_at_request = ?, sales_amount_at_request = ? "
                + "WHERE change_request_id = ?", 9, 240_000L, request.getId());
        when(salesReader.readSales(groupBuy.getId())).thenReturn(Optional.of(new GroupBuySalesReader.GroupBuySales(
                12, 320_000, List.of(new GroupBuySalesReader.ItemQuantity(cream.getProductId(), 8),
                        new GroupBuySalesReader.ItemQuantity(serum.getProductId(), 4)))));
        when(salesReader.countOneToOneInquiriesSince(eq(groupBuy.getId()), any(LocalDateTime.class)))
                .thenReturn(Optional.of(4L));

        adminDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRequest.decisionBasis.ordersAtRequest").value(9))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.ordersNow").value(12))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.ordersSinceRequest").value(3))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.quantityNow").value(12))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.amountNow").value(320_000))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.inquiries.total").value(4))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.inquiries.defectRelated").doesNotExist())
                .andExpect(jsonPath("$.sales.basis").value("LIVE"));
        verify(salesReader).countOneToOneInquiriesSince(eq(groupBuy.getId()), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("조기 마감 판단 근거의 소진율은 계약 최소 물량 대비 판매 수량으로 계산한다")
    void earlyCloseBasisUsesContractMinimumQuantity() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        seedSellerRequest(groupBuy, ChangeRequestType.EARLY_CLOSE);
        when(salesReader.readSales(groupBuy.getId())).thenReturn(Optional.of(new GroupBuySalesReader.GroupBuySales(
                80, 4_200_000, List.of(new GroupBuySalesReader.ItemQuantity(cream.getProductId(), 120),
                        new GroupBuySalesReader.ItemQuantity(serum.getProductId(), 80)))));

        adminDetail(groupBuy.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRequest.decisionBasis.preparedQuantity").value(500))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.quantityNow").value(200))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.sellThroughRate").value(40))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.ordersNow").value(80))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.amountNow").value(4_200_000))
                .andExpect(jsonPath("$.activeRequest.decisionBasis.endsImmediatelyIfApproved").value(true));
    }

    @Test
    @DisplayName("미종결 주문이 있거나 이행 확인이 없으면 막고, 모두 끝나면 정산 포트에만 확인을 위임한다")
    void settlementConfirmationRequiresAllFactsAndDelegates() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.ENDED);
        when(settlementGateway.readStage(groupBuy.getId()))
                .thenReturn(Optional.of(GroupBuySettlementGateway.SettlementStage.WAITING));
        when(salesReader.readClosure(groupBuy.getId())).thenReturn(Optional.of(
                new GroupBuySalesReader.GroupBuyOrderClosure(10, 9, 1, 0, 1, List.of())));
        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.afterEnd.settlement.blockers[0]").value("UNCLOSED_ORDERS"))
                .andExpect(jsonPath("$.afterEnd.settlement.blockers[1]").value("FULFILLMENT_PENDING"))
                .andExpect(jsonPath("$.permissions.canConfirmSettlement").value(false));
        addFulfilledChecks(groupBuy);

        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.afterEnd.settlement.stageSource").value("PORT"))
                .andExpect(jsonPath("$.afterEnd.settlement.blockers[0]").value("UNCLOSED_ORDERS"))
                .andExpect(jsonPath("$.permissions.canConfirmSettlement").value(false));
        adminAction(groupBuy.getId(), "settlement/confirm", null).andExpect(status().isConflict());
        verify(settlementGateway, never()).confirm(groupBuy.getId(), operator.getId());

        when(salesReader.readClosure(groupBuy.getId())).thenReturn(Optional.of(
                new GroupBuySalesReader.GroupBuyOrderClosure(10, 10, 0, 0, 0, List.of())));
        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.afterEnd.settlement.blockers.length()").value(0))
                .andExpect(jsonPath("$.permissions.canConfirmSettlement").value(true));
        adminAction(groupBuy.getId(), "settlement/confirm", null).andExpect(status().isNoContent());
        verify(settlementGateway).confirm(groupBuy.getId(), operator.getId());
        assertThat(reload(groupBuy.getId()).getStatus()).isEqualTo(GroupBuyStatus.ENDED);
    }

    @Test
    @DisplayName("운영자 이슈 개설은 3자 스레드·이력·열린 이슈 1건을 묶고 중복 개설을 막는다")
    void adminIssueOpensExactlyOneThread() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.SUSPENDED);
        when(threadGateway.openAdminIssueThread(any(), eq(GroupBuyIssueType.SETTLEMENT_AMOUNT),
                eq("정산 금액에 이견이 있습니다."))).thenReturn(9_001L);

        adminAction(groupBuy.getId(), "issues",
                Map.of("issueType", "SETTLEMENT_AMOUNT", "content", "정산 금액에 이견이 있습니다."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueId").isNumber())
                .andExpect(jsonPath("$.threadId").value(9_001));
        assertThat(issueRepository.existsByGroupBuyIdAndStatus(groupBuy.getId(), GroupBuyIssueStatus.OPEN)).isTrue();
        adminDetail(groupBuy.getId()).andExpect(jsonPath("$.afterEnd.openIssue.threadId").value(9_001))
                .andExpect(jsonPath("$.afterEnd.openIssue.openerType").value("ADMIN"))
                .andExpect(jsonPath("$.permissions.canOpenIssue").value(false));
        adminAction(groupBuy.getId(), "issues", Map.of("issueType", "ETC", "content", "중복"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_BUY_ISSUE_ALREADY_OPEN"));
        verify(threadGateway).openAdminIssueThread(any(), eq(GroupBuyIssueType.SETTLEMENT_AMOUNT),
                eq("정산 금액에 이견이 있습니다."));
    }

    @Test
    @DisplayName("소명 첨부 다운로드는 해당 공구의 업로드 완료 파일에만 5분짜리 URL을 발급한다")
    void appealAttachmentDownloadChecksOwnershipAndUpload() throws Exception {
        GroupBuy groupBuy = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuy another = seedIn(GroupBuyStatus.IN_PROGRESS);
        GroupBuyAdminSuspension notice = seedNotice(groupBuy.getId(), LocalDateTime.now().plusDays(2));
        GroupBuyAppealAttachment uploaded = addAttachment(notice, true, "evidence.pdf", "appeal/evidence.pdf");
        GroupBuyAppealAttachment pending = addAttachment(notice, false, "pending.pdf", "appeal/pending.pdf");
        when(appealStorage.presignDownload("appeal/evidence.pdf", "evidence.pdf"))
                .thenReturn("https://download.test/evidence");

        adminGet(groupBuy.getId(), "admin-suspension/attachments/" + uploaded.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentId").value(uploaded.getId()))
                .andExpect(jsonPath("$.url").value("https://download.test/evidence"))
                .andExpect(jsonPath("$.fileName").value("evidence.pdf"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
        adminGet(groupBuy.getId(), "admin-suspension/attachments/" + pending.getId())
                .andExpect(status().isNotFound());
        adminGet(another.getId(), "admin-suspension/attachments/" + uploaded.getId())
                .andExpect(status().isNotFound());
        verify(appealStorage).presignDownload("appeal/evidence.pdf", "evidence.pdf");
    }

    private void addFulfilledChecks(GroupBuy groupBuy) {
        transactionTemplate.executeWithoutResult(tx -> {
            GroupBuy reference = groupBuyRepository.findById(groupBuy.getId()).orElseThrow();
            fulfillmentCheckRepository.save(GroupBuyFulfillmentCheck.manual(reference, FulfillmentSide.SELLER,
                    FulfillmentResult.FULFILLED, null, brand.seller().getId(), null, LocalDateTime.now()));
            fulfillmentCheckRepository.save(GroupBuyFulfillmentCheck.manual(reference, FulfillmentSide.CREATOR,
                    FulfillmentResult.FULFILLED, null, creator.getId(), null, LocalDateTime.now()));
        });
    }

    private GroupBuyAppealAttachment addAttachment(GroupBuyAdminSuspension notice, boolean uploaded,
                                                    String name, String key) {
        return transactionTemplate.execute(tx -> {
            GroupBuyAppealAttachment attachment = GroupBuyAppealAttachment.pending(
                    adminSuspensionRepository.findById(notice.getId()).orElseThrow(),
                    brand.seller().getId(), key, name, "application/pdf", 1_000, LocalDateTime.now());
            if (uploaded) {
                attachment.markUploaded(1_000, LocalDateTime.now());
            }
            return appealAttachmentRepository.save(attachment);
        });
    }
}
