package com.mpole.hana.digitaltwin.systemDiagram.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 배선(SystemDiagramWiring)의 JSONB 세그먼트 값 타입.
 * assetId 는 배관(asset_type=PIPE) 에셋에 대한 소프트 참조이며, DB FK 로 강제할 수 없다.
 * start/end 는 Unity 월드좌표(x,y,z).
 */
public record WiringSegment(
        Long assetId,
        Point3 start,
        Point3 end
) {
    @JsonCreator
    public WiringSegment {
        // Jackson record 역직렬화용 컴팩트 생성자
    }

    public record Point3(Float x, Float y, Float z) {
        @JsonCreator
        public Point3 {
        }
    }
}
