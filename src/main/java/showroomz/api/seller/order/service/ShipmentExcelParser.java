package showroomz.api.seller.order.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 송장 엑셀(§34-5) — 양식(주문번호 · 택배사 · 송장번호)과 파싱. 파싱은 셀 값을 읽기만 한다 —
 * 분류(E3)·확정은 커맨드 서비스 몫이다.
 */
@Component
public class ShipmentExcelParser {

    /** @param rowNumber 엑셀 행 번호(1부터 · 헤더 포함) — 오류 안내가 「몇 행」을 지목한다 */
    public record RawRow(int rowNumber, String orderNumber, String carrierText, String trackingNumber) {
    }

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("송장 업로드");
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            Row header = sheet.createRow(0);
            String[] headers = {"주문번호", "택배사", "송장번호"};
            for (int c = 0; c < headers.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(c, 22 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public List<RawRow> parse(InputStream inputStream, int maxRows) {
        DataFormatter formatter = new DataFormatter();
        List<RawRow> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue; // 헤더
                }
                String orderNumber = formatter.formatCellValue(row.getCell(0)).trim();
                String carrierText = formatter.formatCellValue(row.getCell(1)).trim();
                String trackingNumber = formatter.formatCellValue(row.getCell(2)).trim();
                if (orderNumber.isEmpty() && carrierText.isEmpty() && trackingNumber.isEmpty()) {
                    continue; // 빈 행
                }
                rows.add(new RawRow(row.getRowNum() + 1, orderNumber, carrierText, trackingNumber));
                if (rows.size() > maxRows) {
                    throw new BusinessException(ErrorCode.SHIPMENT_FILE_TOO_MANY_ROWS);
                }
            }
            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // xlsx 가 아니거나 손상 — 양식 안내로 돌려보낸다.
            throw new BusinessException(ErrorCode.SHIPMENT_FILE_INVALID);
        }
    }
}
