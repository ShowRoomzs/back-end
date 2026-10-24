package showroomz.api.admin.delivery.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.admin.delivery.docs.AdminRemoteAreaPostcodeControllerDocs;
import showroomz.api.admin.delivery.dto.RemoteAreaPostcodeDto;
import showroomz.api.admin.delivery.service.AdminRemoteAreaPostcodeService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/v1/admin/remote-area-postcodes")
@RequiredArgsConstructor
public class AdminRemoteAreaPostcodeController implements AdminRemoteAreaPostcodeControllerDocs {

    private final AdminRemoteAreaPostcodeService postcodeService;

    @Override
    @GetMapping
    public ResponseEntity<List<RemoteAreaPostcodeDto.Item>> list() {
        return ResponseEntity.ok(postcodeService.list());
    }

    @Override
    @PostMapping
    public ResponseEntity<RemoteAreaPostcodeDto.Item> create(@Valid @RequestBody RemoteAreaPostcodeDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postcodeService.create(request, LocalDateTime.now()));
    }

    @Override
    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RemoteAreaPostcodeDto.BulkResult> bulkCreate(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postcodeService.bulkCreate(file, LocalDateTime.now()));
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        postcodeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
