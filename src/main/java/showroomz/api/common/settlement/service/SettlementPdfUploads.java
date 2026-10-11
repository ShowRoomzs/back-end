package showroomz.api.common.settlement.service;

import org.springframework.web.multipart.MultipartFile;
import showroomz.domain.settlement.service.SettlementTaxDocumentService;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 증빙 PDF 업로드 검증(44 어드민 설계서 7-7 · 스튜디오 3-1) — 인플루언서 세금계산서 첨부 · 브랜드 발행본. PDF 만 · 상한
 * {@code settlement.tax-invoice-attachment-max-bytes}. 타입 헤더만 믿지 않고 파일 머리({@code %PDF-})를 본다.
 */
public final class SettlementPdfUploads {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private SettlementPdfUploads() {
    }

    /** @return 빈 파일이면 null(선택 첨부) */
    public static SettlementTaxDocumentService.Attachment read(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "PDF 는 %dMB 까지 올릴 수 있습니다.".formatted(maxBytes / (1024 * 1024)));
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "파일을 읽을 수 없습니다.");
        }
        if (bytes.length < PDF_MAGIC.length || !Arrays.equals(Arrays.copyOf(bytes, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PDF 파일만 올릴 수 있습니다.");
        }
        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "tax-invoice.pdf" : file.getOriginalFilename();
        return new SettlementTaxDocumentService.Attachment(name, bytes);
    }
}
