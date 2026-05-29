package com.mpole.hana.digitaltwin.dashboard.mechanical.service;

import com.mpole.hana.digitaltwin.dashboard.mechanical.dto.MechanicalPageSnapshotResponse;
import com.mpole.hana.digitaltwin.dashboard.mechanical.dto.MechanicalTabResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardMechanicalService {

    @Transactional(readOnly = true)
    public MechanicalPageSnapshotResponse getPageSnapshot() {
        return MechanicalPageSnapshotResponse.builder()
                .tabs(List.of(
                        tab("heat", "열원 설비"),
                        tab("hvac", "공조 환기"),
                        tab("electric", "전기 설비"),
                        tab("other", "기타")
                ))
                .activeTabId("heat")
                .stub(true)
                .build();
    }

    private static MechanicalTabResponse tab(String id, String label) {
        return MechanicalTabResponse.builder().id(id).label(label).build();
    }
}
