package showroomz.api.common.settlement.dto;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 정산 파일 다운로드 — 명세 xlsx · 원천징수영수증 · 세금계산서 PDF · 신고 자료. 서버가 바이트 스트림으로 내린다(presigned URL 이 밖으로
 * 새지 않게 — 계약서 다운로드와 같은 방식).
 */
public record SettlementFile(String filename, String contentType, byte[] content) {

    public static final String PDF = "application/pdf";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public ResponseEntity<byte[]> toResponse() {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }
}
