package com.mpole.hana.digitaltwin.sop.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopMetaResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOP 메타 API — enum 값·Variant 팔레트 등 클라이언트 동기화용 정적 정보.
 */
@RestController
@RequestMapping("/api/sop")
public class SopMetaController {

    @GetMapping("/meta")
    public ResponseEntity<ApiResponse<SopMetaResponse>> meta() {
        return ResponseEntity.ok(ApiResponse.success("SOP 메타(enum/토큰)", SopMetaResponse.build()));
    }
}
