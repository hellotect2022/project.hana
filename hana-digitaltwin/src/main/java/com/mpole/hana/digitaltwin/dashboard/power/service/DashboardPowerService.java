package com.mpole.hana.digitaltwin.dashboard.power.service;

import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerPageSnapshotResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerTabResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardPowerService {

    @Transactional(readOnly = true)
    public PowerPageSnapshotResponse getPageSnapshot() {
        return PowerPageSnapshotResponse.builder()
                .tabs(List.of(
                        tab("ehv", "특고압"),
                        tab("lv", "저압"),
                        tab("gen", "발전기")
                ))
                .activeTabId("ehv")
                .stub(true)
                .build();
    }

    private static PowerTabResponse tab(String id, String label) {
        return PowerTabResponse.builder().id(id).label(label).build();
    }
}
