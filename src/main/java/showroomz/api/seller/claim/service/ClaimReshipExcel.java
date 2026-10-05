package showroomz.api.seller.claim.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import showroomz.domain.order.type.ClaimReshipColumn;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 재발송 목록 xlsx 생성(35 설계서 3-4 E1) — 브랜드가 고른 컬럼 순서 = 좌→우 열 순서. 선택 컬럼 뒤에 빈 「택배사」
 * 「송장번호」 2열을 항상 붙인다 — 이 파일을 채워 다시 올리는 왕복이 전제다(업로드는 열을 머리글로 찾는다).
 */
@Component
public class ClaimReshipExcel {

    /** 클레임 한 줄 — 컬럼 → 값. */
    public record Line(Map<ClaimReshipColumn, String> values) {
    }

    public byte[] write(List<ClaimReshipColumn> columns, List<Line> lines) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("재발송 목록");
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            int width = columns.size() + 2;
            for (int c = 0; c < width; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(c < columns.size() ? columns.get(c).getHeader()
                        : c == columns.size() ? ClaimReshipColumn.CARRIER_HEADER : ClaimReshipColumn.TRACKING_HEADER);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(c, 18 * 256);
            }
            int rowIndex = 1;
            for (Line line : lines) {
                Row row = sheet.createRow(rowIndex++);
                for (int c = 0; c < columns.size(); c++) {
                    String value = line.values().get(columns.get(c));
                    row.createCell(c).setCellValue(value == null ? "" : value);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
