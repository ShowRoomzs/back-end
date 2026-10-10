package showroomz.domain.settlement.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 표 한 장짜리 xlsx — 원천세 신고 자료(44 어드민 설계서 5-5) · 연간 지급 내역(스튜디오 4-3). 숫자는 숫자 셀, 나머지는 문자 셀이고
 * 마지막에 굵은 합계 행을 둔다(명세 xlsx 와 같은 POI 경로).
 */
@Component
public class SettlementTableExcel {

    public SettlementStatementExcel.File write(String filename, String sheetName, List<String> headers,
                                               List<List<Object>> rows, List<Object> totalRow) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(bold);
                sheet.setColumnWidth(c, 18 * 256);
            }
            int rowIndex = 1;
            for (List<Object> values : rows) {
                fill(sheet.createRow(rowIndex++), values, null);
            }
            if (totalRow != null) {
                fill(sheet.createRow(rowIndex + 1), totalRow, bold);
            }
            workbook.write(out);
            return new SettlementStatementExcel.File(filename, out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void fill(Row row, List<Object> values, CellStyle style) {
        for (int c = 0; c < values.size(); c++) {
            Object value = values.get(c);
            Cell cell = row.createCell(c);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else {
                cell.setCellValue(value == null ? "" : value.toString());
            }
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }
}
