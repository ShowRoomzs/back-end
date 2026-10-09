package showroomz.api.admin.transaction.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.admin.transaction.docs.AdminOrderExceptionControllerDocs;
import showroomz.api.admin.transaction.dto.AdminOrderExceptionDto;
import showroomz.api.admin.transaction.service.AdminOrderExceptionService;
import showroomz.global.dto.PagingRequest;

@RestController
@RequestMapping("/v1/admin/order-exceptions")
@RequiredArgsConstructor
public class AdminOrderExceptionController implements AdminOrderExceptionControllerDocs {

    private final AdminOrderExceptionService exceptionService;

    @Override
    @GetMapping
    public ResponseEntity<AdminOrderExceptionDto.ExceptionPage> getExceptions(
            @RequestParam(value = "tab", defaultValue = "DELAY") AdminOrderExceptionDto.ExceptionTab tab,
            @RequestParam(value = "kind", required = false) AdminOrderExceptionDto.ExceptionKind kind,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(exceptionService.getExceptions(tab, kind, keyword, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<AdminOrderExceptionDto.ExceptionSummary> getSummary() {
        return ResponseEntity.ok(exceptionService.getSummary());
    }
}
