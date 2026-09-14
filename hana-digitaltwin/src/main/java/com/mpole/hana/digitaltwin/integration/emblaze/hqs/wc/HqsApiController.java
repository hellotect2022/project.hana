package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc;

import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsApiResponse;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsBoothStatusData;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto.HqsBoothStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hqs")
@RequiredArgsConstructor
public class HqsApiController {

    private final HqsApiService hqsApiService;

    @PostMapping("/boothStatus")
    public ResponseEntity<HqsApiResponse<HqsBoothStatusData>> getBoothStatus(@Valid @RequestBody HqsBoothStatusRequest request) {
        HqsBoothStatusData data = hqsApiService.getBoothStatusData(request);
        return ResponseEntity.ok(HqsApiResponse.success(data));
    }
}
