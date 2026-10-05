package showroomz.api.seller.order.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
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
import java.math.BigDecimal;
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
                String orderNumber = text(formatter, row.getCell(0));
                String carrierText = text(formatter, row.getCell(1));
                String trackingNumber = text(formatter, row.getCell(2));
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

    /**
     * 첫 시트의 모든 행을 문자열 표로 읽는다 — 머리글 행 포함 · 완전히 빈 행은 건너뛴다. 열 위치가 고정이 아닌 파일
     * (재발송 목록 업로드 — 브랜드마다 컬럼 구성이 다르다)이 머리글로 열을 찾는 데 쓴다. 셀 읽기 규칙은 송장 업로드와 같다.
     */
    public List<List<String>> readRows(InputStream inputStream, int maxRows) {
        DataFormatter formatter = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    cells.add(text(formatter, row.getCell(c)));
                }
                if (cells.stream().allMatch(String::isEmpty)) {
                    continue;
                }
                rows.add(cells);
                if (rows.size() > maxRows + 1) {
                    throw new BusinessException(ErrorCode.SHIPMENT_FILE_TOO_MANY_ROWS);
                }
            }
            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SHIPMENT_FILE_INVALID);
        }
    }

    /**
     * 셀 표시값. 숫자 셀은 <b>자릿수 그대로</b> 읽는다 — 엑셀에 붙여넣은 12자리 송장번호는 일반 서식의 숫자 셀이 되는데,
     * {@link DataFormatter}는 일반 서식의 큰 수를 지수 표기(1.23457E+11)로 돌려준다. 그대로 쓰면 숫자만 남기는 정제를
     * 거쳐 엉뚱한 번호(12345711)가 정상 행으로 등록된다.
     */
    private static String text(DataFormatter formatter, Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell).trim();
    }
}
