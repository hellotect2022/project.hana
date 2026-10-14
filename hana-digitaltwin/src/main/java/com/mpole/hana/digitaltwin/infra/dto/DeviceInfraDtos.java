package com.mpole.hana.digitaltwin.infra.dto;

import com.mpole.hana.common.constant.ConnectionState;
import com.mpole.hana.common.constant.InfraType;
import com.mpole.hana.digitaltwin.infra.entity.DeviceInfra;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 인프라 마스터 관리 API DTO 모음. */
public final class DeviceInfraDtos {

    private DeviceInfraDtos() {
    }

    /** 등록/수정 요청. refInfraCode 는 외부(vw_02) 매핑 키 — 관리자 직접 입력(NVR 등 수동 타입은 null 허용). */
    public record DeviceInfraRequest(
            String refInfraCode,
            String infraName,
            InfraType infraType,
            Boolean active,
            Map<String, Object> properties
    ) {
    }

    public record DeviceInfraResponse(
            Long infraId,
            String refInfraCode,
            String infraName,
            InfraType infraType,
            ConnectionState networkState,
            OffsetDateTime lastCommAt,
            boolean active,
            Map<String, Object> properties,
            long mappedDeviceCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static DeviceInfraResponse from(DeviceInfra e, long mappedDeviceCount) {
            return new DeviceInfraResponse(
                    e.getInfraId(),
                    e.getRefInfraCode(),
                    e.getInfraName(),
                    e.getInfraType(),
                    e.getNetworkState(),
                    e.getLastCommAt(),
                    e.isActive(),
                    e.getProperties(),
                    mappedDeviceCount,
                    e.getCreatedAt(),
                    e.getUpdatedAt()
            );
        }
    }

    /** 장비 소속 지정/해제. infraId=null 이면 매핑 해제. */
    public record InfraMappingRequest(
            Long deviceId,
            Long infraId
    ) {
    }

    /** 자동 산정 요청. excludeRefCodes = 공유신호(브로드캐스트) 인프라 코드 제외 목록(선택). */
    public record AutoAssignRequest(
            List<String> excludeRefCodes
    ) {
    }

    /** 인프라 기준 매핑 장비 1건(관리 화면 목록). */
    public record MappedDeviceResponse(
            Long deviceId,
            String deviceName,
            String deviceDisplayName,
            String categoryName
    ) {
    }

    /** 매핑 후보 장비 1건 — 타 인프라 소속이면 현재 소속(infraId/infraName)을 함께 노출. */
    public record CandidateDeviceResponse(
            Long deviceId,
            String deviceName,
            String deviceDisplayName,
            String categoryName,
            Long infraId,
            String infraName
    ) {
    }

    /** 인프라 기준 일괄 매핑/해제 요청. */
    public record BulkMappingRequest(
            List<Long> deviceIds
    ) {
    }

    /** 정합성 리포트 — 자동/수동 매핑의 빈틈 가시화. */
    public record ConsistencyResponse(
            List<DeviceBrief> unmappedXnDevices,   // XN ref 보유 + infra_id NULL
            List<MultiRefDevice> multiRefDevices   // 서로 다른 ref 2개 이상 참조(오귀속 검수 후보)
    ) {
        public record DeviceBrief(Long deviceId, String deviceName) {
        }

        public record MultiRefDevice(Long deviceId, String deviceName, long refCount) {
        }
    }
}
