package showroomz.api.seller.order.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import showroomz.domain.order.type.PurchaseOrderColumn;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 발주서 xlsx 생성(§34-4) — 고정 양식 없음. 브랜드가 고른 컬럼 순서 = 좌→우 열 순서.
 * 행 단위는 <b>주문 항목</b>(SKU)이다 — 옵션·수량이 항목 축이라 항목이 행이어야 옮겨 적지 않는다.
 */
@Component
public class PurchaseOrderExcelWriter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 항목 한 줄 — 수취인·주소는 하위주문 값을 반복한다. */
    public record Line(
            String orderNumber,
            String subOrderNumber,
            String recipient,
            String phone,
            String zipCode,
            String address,
            String productName,
            String optionName,
            int quantity,
            String deliveryMemo,
            String groupBuyName,
            LocalDateTime orderedAt,
            int amount
    ) {
    }

    public byte[] write(List<PurchaseOrderColumn> columns, List<Line> lines) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("발주서");
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(columns.get(c).getHeader());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(c, 18 * 256);
            }
            int rowIndex = 1;
            for (Line line : lines) {
                Row row = sheet.createRow(rowIndex++);
                for (int c = 0; c < columns.size(); c++) {
                    setValue(row.createCell(c), columns.get(c), line);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void setValue(Cell cell, PurchaseOrderColumn column, Line line) {
        switch (column) {
            case ORDER_NUMBER -> cell.setCellValue(nullSafe(line.orderNumber()));
            case SUB_ORDER_NUMBER -> cell.setCellValue(nullSafe(line.subOrderNumber()));
            case RECIPIENT -> cell.setCellValue(nullSafe(line.recipient()));
            case PHONE -> cell.setCellValue(nullSafe(line.phone()));
            case ZIP_CODE -> cell.setCellValue(nullSafe(line.zipCode()));
            case ADDRESS -> cell.setCellValue(nullSafe(line.address()));
            case PRODUCT_NAME -> cell.setCellValue(nullSafe(line.productName()));
            case OPTION -> cell.setCellValue(nullSafe(line.optionName()));
            case QUANTITY -> cell.setCellValue(line.quantity());
            case DELIVERY_MEMO -> cell.setCellValue(nullSafe(line.deliveryMemo()));
            case GROUP_BUY_NAME -> cell.setCellValue(nullSafe(line.groupBuyName()));
            case ORDERED_AT -> cell.setCellValue(line.orderedAt() == null ? "" : DATE_TIME.format(line.orderedAt()));
            case PAID_AMOUNT -> cell.setCellValue(line.amount());
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
