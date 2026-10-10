package showroomz.api.creator.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.settlement.dto.SettlementFile;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.domain.settlement.repository.SettlementTaxDocumentRepository;
import showroomz.domain.settlement.service.SettlementStatementExcel;
import showroomz.domain.settlement.service.SettlementTableExcel;
import showroomz.domain.settlement.service.SettlementTaxDocumentService;
import showroomz.domain.settlement.type.TaxDocumentStatus;
import showroomz.domain.settlement.type.TaxDocumentType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 스튜디오 다운로드(44 스튜디오 설계서 4-2 ~ 4-4) — 원천징수영수증 · 제출한 세금계산서 PDF · 연간 지급 내역. 남의 정산은 404 다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorSettlementDocumentService {

    private final CreatorSettlementQueryService queryService;
    private final SettlementRepository settlementRepository;
    private final SettlementTaxDocumentRepository documentRepository;
    private final SettlementTaxDocumentService taxDocumentService;
    private final SettlementTableExcel tableExcel;

    /** 4-2 — 생성 완료 전이면 409 SETTLEMENT_RECEIPT_NOT_READY. */
    public SettlementFile withholdingReceipt(String creatorEmail, Long settlementId) {
        Settlement s = queryService.loadOwned(settlementId, queryService.resolveCreator(creatorEmail));
        SettlementTaxDocument receipt = documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(s.getId(),
                        TaxDocumentType.WITHHOLDING_RECEIPT)
                .filter(d -> d.getStatus() == TaxDocumentStatus.GENERATED && d.hasFile())
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_RECEIPT_NOT_READY));
        return new SettlementFile(receipt.getFileName(), SettlementFile.PDF, taxDocumentService.readFile(receipt));
    }

    /** 4-4 — 제출한 세금계산서 첨부. 첨부가 없었으면 404. */
    public SettlementFile taxInvoiceAttachment(String creatorEmail, Long settlementId) {
        Settlement s = queryService.loadOwned(settlementId, queryService.resolveCreator(creatorEmail));
        SettlementTaxDocument invoice = documentRepository.findBySettlementIdAndTypeAndClawbackIdIsNull(s.getId(),
                        TaxDocumentType.CREATOR_TAX_INVOICE)
                .filter(SettlementTaxDocument::hasFile)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND));
        return new SettlementFile(invoice.getFileName(), SettlementFile.PDF, taxDocumentService.readFile(invoice));
    }

    /**
     * 4-3 — 내 몫 지급 완료일이 그 해에 속한 정산. 비사업자는 원천징수 열(종합소득세 신고용) · 사업자는 부가세 · 승인번호 열.
     * 데이터가 없으면 빈 파일이 아니라 404(빈 엑셀은 「올해 소득 0」으로 읽힌다).
     */
    public SettlementFile annualStatement(String creatorEmail, Integer year) {
        Creator me = queryService.resolveCreator(creatorEmail);
        int target = year == null ? Year.now().getValue() : year;
        List<Object[]> rows = settlementRepository.findCreatorPaidBetween(me.getId(),
                LocalDateTime.of(target, 1, 1, 0, 0), LocalDateTime.of(target + 1, 1, 1, 0, 0));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        boolean business = me.getBusinessType() == CreatorBusinessType.BUSINESS;
        Map<Long, String> approvals = business ? approvalNumbers(rows) : Map.of();
        List<List<Object>> table = new ArrayList<>();
        long[] totals = new long[5];
        for (Object[] row : rows) {
            Settlement s = (Settlement) row[0];
            LocalDateTime paidAt = (LocalDateTime) row[1];
            long incomeTax = BigDecimal.valueOf(s.getRewardAfterClawback()).multiply(s.getWithholdingIncomeRate())
                    .setScale(0, RoundingMode.DOWN).longValue();
            long localTax = s.getWithholdingAmount() - incomeTax;
            List<Object> line = new ArrayList<>(List.of(s.getSettlementNumber(), s.getContract().getTitle(),
                    s.getMarket().getMarketName(), paidAt.toLocalDate().toString(), s.getRewardAmount(),
                    s.getRewardClawbackAmount()));
            if (business) {
                line.add(s.getCreatorVatAmount());
                line.add(approvals.getOrDefault(s.getId(), ""));
            } else {
                line.add(s.isBusinessCreator() ? 0L : incomeTax);
                line.add(s.isBusinessCreator() ? 0L : localTax);
            }
            line.add(s.getCreatorPayoutAmount());
            table.add(line);
            totals[0] += s.getRewardAmount();
            totals[1] += s.getRewardClawbackAmount();
            totals[2] += business ? s.getCreatorVatAmount() : (s.isBusinessCreator() ? 0 : incomeTax);
            totals[3] += business ? 0 : (s.isBusinessCreator() ? 0 : localTax);
            totals[4] += s.getCreatorPayoutAmount();
        }
        List<String> headers = business
                ? List.of("정산번호", "공구명", "브랜드", "지급일", "판매 리워드", "차감", "부가세", "세금계산서 승인번호", "실지급액")
                : List.of("정산번호", "공구명", "브랜드", "지급일", "판매 리워드", "차감", "소득세", "지방소득세", "실지급액");
        List<Object> totalRow = business
                ? List.of("합계", "", "", "", totals[0], totals[1], totals[2], "", totals[4])
                : List.of("합계", "", "", "", totals[0], totals[1], totals[2], totals[3], totals[4]);
        SettlementStatementExcel.File file = tableExcel.write("연간지급내역_%d.xlsx".formatted(target),
                "%d 지급 내역".formatted(target), headers, table, totalRow);
        return new SettlementFile(file.filename(), SettlementFile.XLSX, file.content());
    }

    private Map<Long, String> approvalNumbers(List<Object[]> rows) {
        List<Long> ids = rows.stream().map(row -> ((Settlement) row[0]).getId()).toList();
        Map<Long, String> approvals = new java.util.HashMap<>();
        for (SettlementTaxDocument d : documentRepository.findBySettlementIdIn(ids)) {
            if (d.getType() == TaxDocumentType.CREATOR_TAX_INVOICE && d.getApprovalNumber() != null) {
                approvals.put(d.getSettlementId(), d.getApprovalNumber());
            }
        }
        return approvals;
    }
}
