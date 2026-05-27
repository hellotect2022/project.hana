package com.mpole.hana.digitaltwin.location.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityLocationResponse;
import com.mpole.hana.digitaltwin.location.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/location-info")
    public ResponseEntity<ApiResponse<List<UnityLocationResponse>>> getLocationInfo() {
        return ResponseEntity.ok(ApiResponse.success(
                "Unity 위치 트리", locationService.findUnityLocationTree()));
    }
}
