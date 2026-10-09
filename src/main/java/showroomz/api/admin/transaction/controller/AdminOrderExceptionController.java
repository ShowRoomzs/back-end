package showroomz.api.admin.transaction.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.transaction.docs.AdminOrderExceptionControllerDocs;
import showroomz.api.admin.transaction.dto.AdminTransactionDto;
import showroomz.api.admin.transaction.service.AdminOrderExceptionService;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/order-exceptions")
@RequiredArgsConstructor
public class AdminOrderExceptionController implements AdminOrderExceptionControllerDocs {

    private final AdminOrderExceptionService exceptionService;

    @Override
    @GetMapping
    public ResponseEntity<List<AdminTransactionDto.ExceptionItem>> getExceptions(
            @RequestParam(value = "tab", defaultValue = "DELAY") AdminTransactionDto.ExceptionTab tab) {
        return ResponseEntity.ok(exceptionService.getExceptions(tab));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminTransactionDto.ExceptionSummary> getSummary() {
        return ResponseEntity.ok(exceptionService.getSummary());
    }
}
