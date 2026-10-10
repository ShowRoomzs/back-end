package showroomz.api.creator.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.common.settlement.service.SettlementPdfUploads;
import showroomz.api.creator.settlement.dto.CreatorSettlementDto;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.settlement.entity.SettlementTaxDocument;
import showroomz.domain.settlement.service.SettlementTaxDocumentService;
import showroomz.global.config.properties.SettlementProperties;

import java.time.LocalDateTime;

/**
 * 스튜디오 쓰기 — 세금계산서 승인번호 제출(44 스튜디오 설계서 3-1). 소유 검사 · 첨부 검증만 하고 도메인 서비스
 * ({@link SettlementTaxDocumentService#submitCreatorInvoice})를 부른다. 조정 요청 · 응답은 이슈 스레드 API 다.
 */
@Service
@RequiredArgsConstructor
public class CreatorSettlementCommandService {

    private final CreatorSettlementQueryService queryService;
    private final SettlementTaxDocumentService taxDocumentService;
    private final CreatorSettlementBlocks blocks;
    private final SettlementProperties properties;

    public CreatorSettlementDto.TaxInvoiceSubmitResponse submitTaxInvoice(String creatorEmail, Long settlementId,
                                                                          String approvalNumber,
                                                                          MultipartFile attachment) {
        Creator me = queryService.resolveCreator(creatorEmail);
        SettlementTaxDocumentService.Attachment file = SettlementPdfUploads.read(attachment,
                properties.getTaxInvoiceAttachmentMaxBytes());
        SettlementTaxDocument submitted = taxDocumentService.submitCreatorInvoice(settlementId, me.getId(),
                approvalNumber, file, LocalDateTime.now());
        return new CreatorSettlementDto.TaxInvoiceSubmitResponse(settlementId, blocks.taxInvoice(submitted));
    }
}
