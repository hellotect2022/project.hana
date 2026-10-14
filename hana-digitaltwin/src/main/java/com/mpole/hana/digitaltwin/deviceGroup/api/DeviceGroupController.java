package com.mpole.hana.digitaltwin.deviceGroup.api;

import com.mpole.hana.digitaltwin.deviceGroup.dto.*;
import com.mpole.hana.digitaltwin.deviceGroup.service.DeviceGroupService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 장비 논리 그룹 관리 API.
 *
 * <p>기존 {@code /api/device} 에 붙여 카테고리 관리 API 와 대칭으로 둔다.
 * 경로 식별자는 숫자 {@code groupId} 다({@code /device-categories/{categoryId}} 와 같은 규칙).
 * 외부 연동용 문자열 식별자({@code groupCode})로 찾을 때는 {@code /by-code/{groupCode}} 를 쓴다.</p>
 *
 * <p>화장실 부스·롤스크린 구역이 여기서 관리된다. zone 계층(부스 = depth 1 zone)을
 * 대체하는 구조이지만, <b>기존 조회 로직은 아직 그대로 동작한다</b> — 두 구조를 나란히 두고
 * 같은 결과가 나오는지 대조하는 단계다.</p>
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Tag(name = "Device Group", description = "장비 논리 그룹 관리 API (화장실 부스·롤스크린 구역 등)")
public class DeviceGroupController {

    private final DeviceGroupService deviceGroupService;

    @GetMapping("/device-groups/meta")
    @Operation(summary = "그룹 코드 목록",
            description = "groupType·role 코드와 한글 라벨. 관리자 화면 select 를 채우는 용도다. "
                    + "DB 가 CHECK 없는 varchar 라 값을 늘릴 수 있으므로 강제 목록이 아니라 추천 목록이며, "
                    + "groupTypes 에는 DB 에 실제로 쓰이고 있는 값도 합쳐서 내려간다.")
    public ResponseEntity<ApiResponse<DeviceGroupMetaResponse>> getMeta() {
        return ResponseEntity.ok(ApiResponse.success("장비 그룹 코드 목록", deviceGroupService.getMeta()));
    }

    @GetMapping("/device-groups")
    @Operation(summary = "그룹 목록",
            description = "groupType 으로 용도를, active 로 활성 여부를 좁히고 keyword 로 "
                    + "groupCode·groupName 을 검색한다. 셋 다 생략 가능하며 생략하면 전체다. "
                    + "기본 정렬은 groupType, groupCode 오름차순. 멤버 배열 없이 memberCount 만 내려간다.")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DeviceGroupResponse>>> getGroups(
            @Parameter(description = "용도. 생략하면 전체") @RequestParam(required = false) String groupType,
            @Parameter(description = "groupCode·groupName 부분일치(대소문자 무시)") @RequestParam(required = false) String keyword,
            @Parameter(description = "활성 여부. 생략하면 활성·비활성 모두") @RequestParam(required = false) Boolean active,
            // 용도끼리 모아서 보여준다. 같은 용도 안에서는 코드 순 — 부스는 화장실별로,
            // 롤스크린은 층·구역 순으로 자연스럽게 묶인다.
            @Parameter(hidden = true) @PageableDefault(size = 20, sort = {"groupType", "groupCode"}, direction = Sort.Direction.ASC) Pageable pageable) {

        Page<DeviceGroupResponse> page = deviceGroupService.getGroups(groupType, keyword, active, pageable);
        return ResponseEntity.ok(ApiResponse.success("장비 그룹 목록 조회", page));
    }

    @GetMapping("/device-groups/{groupId}/candidates")
    @Operation(summary = "그룹에 넣을 후보 장비",
            description = "이미 그 그룹에 들어 있는 장비만 제외한다. 다른 그룹 소속은 제외하지 않는다 — "
                    + "롤스크린 모터가 '3층 전체'와 '3층 서쪽'에 동시에 속하는 것은 정상이기 때문이다. "
                    + "대신 기존 소속 그룹을 memberOf 로 함께 내려주니 화면에서 보여주면 된다.")
    public ResponseEntity<ApiResponse<List<DeviceGroupCandidateResponse>>> getCandidates(
            @PathVariable Long groupId,
            @Parameter(description = "소분류 카테고리로 좁히기(선택)") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "deviceName·deviceKey·표시명 부분일치") @RequestParam(required = false) String keyword,
            @Parameter(description = "최대 건수(기본·상한 300)") @RequestParam(required = false) Integer limit) {

        List<DeviceGroupCandidateResponse> response =
                deviceGroupService.getCandidates(groupId, categoryId, keyword, limit);
        return ResponseEntity.ok(ApiResponse.success("그룹 배정 후보 장비 조회", response));
    }

    @GetMapping("/device-groups/by-code/{groupCode}")
    @Operation(summary = "그룹 상세 (코드로 조회)",
            description = "외부 연동이 쓰는 문자열 식별자로 찾는다. 예: WC_4F_M01_B01 · ROLL_F003_Z001")
    public ResponseEntity<ApiResponse<DeviceGroupResponse>> getGroupByCode(@PathVariable String groupCode) {
        return ResponseEntity.ok(ApiResponse.success("장비 그룹 조회", deviceGroupService.getGroupByCode(groupCode)));
    }

    @GetMapping("/device-groups/{groupId}")
    @Operation(summary = "그룹 상세", description = "소속 장비 목록을 포함한다.")
    public ResponseEntity<ApiResponse<DeviceGroupResponse>> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("장비 그룹 조회", deviceGroupService.getGroup(groupId)));
    }

    @PostMapping("/device-groups")
    @Operation(summary = "그룹 생성",
            description = "groupCode 는 클라이언트가 정한다 — 외부 연동 식별자를 겸한다(HQS wcBoothId). "
                    + "장비 없이 그룹만 먼저 만들 수 있다.")
    public ResponseEntity<ApiResponse<DeviceGroupResponse>> createGroup(
            @Valid @RequestBody DeviceGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success("장비 그룹 생성", deviceGroupService.createGroup(request)));
    }

    @PutMapping("/device-groups/{groupId}")
    @Operation(summary = "그룹 수정",
            description = "groupCode 는 변경할 수 있다(중복이면 409). 단 외부 연동(HQS wcBoothId)이 이 값을 "
                    + "식별자로 쓰므로 바꾸면 상대 시스템이 보는 식별자가 바뀐다. "
                    + "groupType 은 변경 불가 — 용도를 바꾸면 이미 배정된 장비가 엉뚱한 곳에 남는다. "
                    + "새 그룹을 만들어 옮길 것.")
    public ResponseEntity<ApiResponse<DeviceGroupResponse>> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody DeviceGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success("장비 그룹 수정", deviceGroupService.updateGroup(groupId, request)));
    }

    @DeleteMapping("/device-groups/{groupId}")
    @Operation(summary = "그룹 삭제", description = "소속 멤버 매핑도 함께 사라진다. 장비 자체와 장비 위치는 영향받지 않는다.")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable Long groupId) {
        deviceGroupService.deleteGroup(groupId);
        return ResponseEntity.ok(ApiResponse.success("장비 그룹 삭제"));
    }

    @GetMapping("/device-groups/{groupId}/members")
    @Operation(summary = "그룹 소속 장비 목록")
    public ResponseEntity<ApiResponse<List<DeviceGroupMemberResponse>>> getMembers(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("그룹 소속 장비 조회", deviceGroupService.getMembers(groupId)));
    }

    @PutMapping("/device-groups/{groupId}/members")
    @Operation(summary = "그룹 소속 장비 전체 교체",
            description = "개별 추가/삭제가 아니라 devices 목록을 통째로 받는다. 배열 순서가 그대로 sortOrder 가 된다. "
                    + "각 항목의 role 을 생략하면 서버가 카테고리로 추정한다(MRS-CONTROL·RL → CONTROLLER, "
                    + "MRS·OCC·TB → MONITOR). 빈 배열은 전체 해제이며 그룹 자체는 남는다.")
    public ResponseEntity<ApiResponse<List<DeviceGroupMemberResponse>>> replaceMembers(
            @PathVariable Long groupId,
            @Valid @RequestBody DeviceGroupMemberBulkRequest request) {
        return ResponseEntity.ok(ApiResponse.success("그룹 소속 장비 저장",
                deviceGroupService.replaceMembers(groupId, request)));
    }
}
