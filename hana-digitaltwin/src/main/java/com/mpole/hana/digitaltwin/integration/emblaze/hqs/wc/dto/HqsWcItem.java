package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HqsWcItem {

    private String wcId;
    private String gender;
    private List<HqsWcBoothItem> wcBoothList;
}
