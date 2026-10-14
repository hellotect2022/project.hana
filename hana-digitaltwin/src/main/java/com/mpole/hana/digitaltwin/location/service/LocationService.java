//package com.mpole.hana.digitaltwin.location.service;
//
//import com.mpole.hana.digitaltwin.location.dto.UnityLocationResponse;
//import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
//import com.mpole.hana.digitaltwin.location.repository.UnityLocBuildingRepository;
//import com.mpole.hana.digitaltwin.location.repository.UnityLocFloorRepository;
//import com.mpole.hana.digitaltwin.location.repository.UnityLocZoneRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class LocationService {
//
//    private final UnityLocBuildingRepository buildingRepository;
//    private final UnityLocFloorRepository floorRepository;
//    private final UnityLocZoneRepository zoneRepository;
//
//    /** GET /api/device/location-info 대체 — Unity 씬 트리 */
//    @Transactional(readOnly = true)
//    public List<UnityLocationResponse> findUnityLocationTree() {
//        return buildingRepository.fetchAllWithFloorsAndZones().stream()
//                .map(UnityLocationResponse::from)
//                .toList();
//    }
//
//    public UnityLocZone getZoneReference(Long zoneId) {
//        return zoneRepository.getReferenceById(zoneId);
//    }
//}
