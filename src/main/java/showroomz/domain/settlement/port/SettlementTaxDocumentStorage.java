package showroomz.domain.settlement.port;

/**
 * 증빙 파일 저장소(44 어드민 설계서 5절) — 원천징수영수증 · 브랜드 세금계산서 발행본 · 인플루언서 세금계산서 첨부. S3 private 에
 * 두고 서버가 <b>바이트 스트림</b>으로 내려준다(계약서 다운로드와 같이 — presigned URL 이 밖으로 새지 않게).
 */
public interface SettlementTaxDocumentStorage {

    String PDF = "application/pdf";

    /** 키 {@code settlements/{settlementId}/tax-document/{documentId}-{uuid}.pdf} — 재제출은 새 키다. 롤백되면 지운다. */
    String put(Long settlementId, Long documentId, byte[] bytes);

    byte[] read(String key);
}
