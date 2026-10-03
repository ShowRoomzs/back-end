package showroomz.api.seller.order.service;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import showroomz.api.seller.order.service.PurchaseOrderExcelWriter.Line;
import showroomz.domain.order.type.PurchaseOrderColumn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("발주서 xlsx(§34-4) — 고른 컬럼 순서 = 좌→우 열 순서 · 행 = 주문 항목")
class PurchaseOrderExcelWriterTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 3, 9, 5, 42);

    private final PurchaseOrderExcelWriter writer = new PurchaseOrderExcelWriter();

    @Test
    @DisplayName("13종 컬럼이 각자 자기 값으로 채워진다 — 수량·결제금액은 숫자 셀 · 주문일시는 분까지")
    void everyColumnMapsToItsValue() throws IOException {
        List<PurchaseOrderColumn> columns = Arrays.asList(PurchaseOrderColumn.values());
        Line line = new Line("20261003-000001", "20261003-000001-01", "김수민", "010-1234-5678", "06234",
                "서울 강남구 테헤란로 000 12층", "글로우 크림 50ml", "기본", 2, "문 앞에 놓아주세요",
                "글로우 크림 앵콜 공구", PAID_AT, 54_400);

        try (Workbook workbook = read(writer.write(columns, List.of(line)))) {
            Sheet sheet = workbook.getSheet("발주서");
            Row header = sheet.getRow(0);
            for (int c = 0; c < columns.size(); c++) {
                assertThat(header.getCell(c).getStringCellValue()).isEqualTo(columns.get(c).getHeader());
            }
            assertThat(workbook.getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold()).isTrue();

            Row row = sheet.getRow(1);
            assertThat(text(row, columns, PurchaseOrderColumn.ORDER_NUMBER)).isEqualTo("20261003-000001");
            assertThat(text(row, columns, PurchaseOrderColumn.SUB_ORDER_NUMBER)).isEqualTo("20261003-000001-01");
            assertThat(text(row, columns, PurchaseOrderColumn.RECIPIENT)).isEqualTo("김수민");
            assertThat(text(row, columns, PurchaseOrderColumn.PHONE)).isEqualTo("010-1234-5678");
            assertThat(text(row, columns, PurchaseOrderColumn.ZIP_CODE)).isEqualTo("06234");
            assertThat(text(row, columns, PurchaseOrderColumn.ADDRESS)).isEqualTo("서울 강남구 테헤란로 000 12층");
            assertThat(text(row, columns, PurchaseOrderColumn.PRODUCT_NAME)).isEqualTo("글로우 크림 50ml");
            assertThat(text(row, columns, PurchaseOrderColumn.OPTION)).isEqualTo("기본");
            assertThat(text(row, columns, PurchaseOrderColumn.DELIVERY_MEMO)).isEqualTo("문 앞에 놓아주세요");
            assertThat(text(row, columns, PurchaseOrderColumn.GROUP_BUY_NAME)).isEqualTo("글로우 크림 앵콜 공구");
            assertThat(text(row, columns, PurchaseOrderColumn.ORDERED_AT)).isEqualTo("2026-10-03 09:05");
            var quantity = row.getCell(columns.indexOf(PurchaseOrderColumn.QUANTITY));
            assertThat(quantity.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(quantity.getNumericCellValue()).isEqualTo(2d);
            assertThat(row.getCell(columns.indexOf(PurchaseOrderColumn.PAID_AMOUNT)).getNumericCellValue())
                    .isEqualTo(54_400d);
        }
    }

    @Test
    @DisplayName("열 순서는 요청 순서를 따른다 · null 값은 빈 칸으로")
    void columnOrderAndNulls() throws IOException {
        List<PurchaseOrderColumn> columns = List.of(PurchaseOrderColumn.QUANTITY, PurchaseOrderColumn.DELIVERY_MEMO,
                PurchaseOrderColumn.ORDERED_AT, PurchaseOrderColumn.RECIPIENT);
        Line line = new Line("20261003-000001", null, "김수민", null, null, null, "글로우 세럼 30ml", null, 1,
                null, null, null, 24_000);

        try (Workbook workbook = read(writer.write(columns, List.of(line, line)))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("수량");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("수취인");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            Row row = sheet.getRow(2);
            assertThat(row.getCell(1).getStringCellValue()).isEmpty();
            assertThat(row.getCell(2).getStringCellValue()).isEmpty();
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("김수민");
        }
    }

    @Test
    @DisplayName("항목이 없어도 헤더만 있는 파일을 만든다")
    void headerOnly() throws IOException {
        try (Workbook workbook = read(writer.write(List.of(PurchaseOrderColumn.ORDER_NUMBER), List.of()))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
        }
    }

    private static Workbook read(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private static String text(Row row, List<PurchaseOrderColumn> columns, PurchaseOrderColumn column) {
        return row.getCell(columns.indexOf(column)).getStringCellValue();
    }
}
