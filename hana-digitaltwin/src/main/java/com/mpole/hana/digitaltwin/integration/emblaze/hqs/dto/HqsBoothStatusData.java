package com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HqsBoothStatusData {

    private String bldgId;
    private String florId;
    private List<HqsWcItem> wcList;
}
