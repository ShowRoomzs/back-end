package showroomz.api.app.terms.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import showroomz.api.app.terms.docs.TermsControllerDocs;
import showroomz.api.app.terms.dto.TermsDocumentDetailResponse;
import showroomz.api.app.terms.dto.TermsDocumentResponse;
import showroomz.api.app.terms.service.TermsService;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;

import java.util.List;

@RestController
@RequestMapping("/v1/common/terms")
@RequiredArgsConstructor
public class TermsController implements TermsControllerDocs {

    private final TermsService termsService;

    @Override
    @GetMapping
    public ResponseEntity<List<TermsDocumentResponse>> getTerms(
            @RequestParam(value = "type", required = false) TermsType type,
            @RequestParam(value = "target", required = false) TermsTarget target) {
        return ResponseEntity.ok(termsService.getTerms(type, target));
    }

    @Override
    @GetMapping("/{documentId}")
    public ResponseEntity<TermsDocumentDetailResponse> getTermsDetail(
            @PathVariable("documentId") Long documentId) {
        return ResponseEntity.ok(termsService.getTermsDetail(documentId));
    }
}
