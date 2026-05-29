package com.mpole.hana.digitaltwin.dashboard.situation.service;

import com.mpole.hana.digitaltwin.dashboard.situation.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 통합현황 데이터 제공.
 * <p>현재는 프론트 mock과 동일한 스텁. 집계·시계열은 repository 연동 후 교체 예정.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardSituationService {

    @Transactional(readOnly = true)
    public List<SituationTopKpiItemResponse> getTopKpis() {
        return stubBoard().topKpis();
    }

    @Transactional(readOnly = true)
    public SituationBoardResponse getBoard() {
        return stubBoard();
    }

    private static SituationBoardResponse stubBoard() {
        return SituationBoardResponse.builder()
                .topKpis(List.of(
                        SituationTopKpiItemResponse.builder()
                                .id("alarm").label("알람 발생 건수").value("2").unit("건").tone("alarm").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("gas").label("금일 가스 사용량").value("1,240").unit("Nm³").tone("gas").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("power").label("금일 전력 사용량").value("42.6").unit("MWh").tone("power").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("renew").label("신재생 에너지 생산량").value("3.8").unit("MWh").tone("renew").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("heat").label("지역난방 사용량").value("18.2").unit("Gcal").tone("heat").build(),
                        SituationTopKpiItemResponse.builder()
                                .id("equip").label("전체 설비 가동 현황").tone("equip")
                                .run(118).stop(4).fault(3).build(),
                        SituationTopKpiItemResponse.builder()
                                .id("env").label("기준층 실내 환경").tone("env")
                                .temp(23.4).humidity(48.0).build()
                ))
                .mechanicalTabs(List.of("열원설비", "공조환기", "전기설비", "기타"))
                .mechanicalEquipmentByTab(stubMechanicalByTab())
                .powerMonitor(SituationPowerMonitorResponse.builder()
                        .kw(1824).voltage(380).frequency(60).peak(2140).powerFactor(0.94)
                        .dailyMwh(42.6).vsYesterdayPct(4.2).demandOccurTime("12:34")
                        .series(List.of(
                                point("00:00", 820),
                                point("06:00", 1650),
                                point("12:00", 3820),
                                point("18:00", 2910),
                                point("24:00", 940)
                        ))
                        .build())
                .fireStatus(SituationFireStatusResponse.builder()
                        .alarmCount(1).sensorCount(2)
                        .devices(List.of(
                                fireDevice("recv", "화재수신반", 12),
                                fireDevice("det", "감지기", 248),
                                fireDevice("relay", "중계기", 36)
                        ))
                        .build())
                .accessStatus(List.of(
                        access("open", "열림", 3),
                        access("closed", "닫힘", 42),
                        access("forcedOpen", "강제열림", 0),
                        access("forcedClosed", "강제닫힘", 1)
                ))
                .meteringUtilities(List.of(
                        utility("elec", "전기", 42, 1),
                        utility("water", "수도", 18, 0),
                        utility("hot", "온수", 12, 1),
                        utility("gas", "가스", 8, 0),
                        utility("hvac", "냉난방", 24, 1),
                        utility("drain", "영구배수", 6, 0),
                        utility("reuse", "중수/우수", 10, 0),
                        utility("flow", "유량", 8, 0)
                ))
                .lightingStatus(SituationLightingStatusResponse.builder()
                        .modes(List.of("일반 조명", "화장실 조명", "지하주차장 조명"))
                        .byMode(Map.of(
                                "일반 조명", lightingCounts(186, 42, 2),
                                "화장실 조명", lightingCounts(48, 12, 1),
                                "지하주차장 조명", lightingCounts(92, 28, 0)
                        ))
                        .build())
                .parkingStatus(SituationParkingStatusResponse.builder().occupied(142).total(280).build())
                .energyRenew(List.of(
                        renew("02:00", 15, 10),
                        renew("04:00", 15, 10),
                        renew("06:00", 40, 35),
                        renew("08:00", 145, 105),
                        renew("10:00", 175, 100),
                        renew("12:00", 185, 85),
                        renew("14:00", 195, 75),
                        renew("16:00", 175, 100),
                        renew("18:00", 145, 105),
                        renew("20:00", 40, 35),
                        renew("22:00", 15, 10),
                        renew("24:00", 15, 10)
                ))
                .build();
    }

    private static Map<String, List<SituationMechanicalEquipmentResponse>> stubMechanicalByTab() {
        Map<String, List<SituationMechanicalEquipmentResponse>> map = new LinkedHashMap<>();
        map.put("열원설비", List.of(
                equip("abs", "흡수식 냉동기", 12, 2, 1),
                equip("compact", "컴팩트 유닛 설비", 8, 1, 0),
                equip("ehp", "EHP", 14, 0, 1),
                equip("tower", "냉각탑", 6, 1, 0),
                equip("boiler", "보일러", 4, 0, 0),
                equip("hex", "열교환기", 10, 2, 1)
        ));
        map.put("공조환기", List.of(
                equip("ahu", "AHU", 18, 2, 1),
                equip("fcu", "FCU", 42, 5, 2),
                equip("vent", "환기설비", 9, 1, 0),
                equip("pac", "공조기", 7, 0, 1),
                equip("filter", "필터 유닛", 11, 3, 0)
        ));
        map.put("전기설비", List.of(
                equip("ups", "UPS", 6, 0, 1),
                equip("mcc", "MCC", 8, 1, 0),
                equip("panel", "분전반", 24, 2, 1),
                equip("xfmr", "변압기", 4, 0, 0),
                equip("gen", "비상발전기", 2, 1, 0)
        ));
        map.put("기타", List.of(
                equip("wp", "급수펌프", 5, 1, 0),
                equip("dp", "배수펌프", 4, 0, 1),
                equip("sp", "오수펌프", 3, 1, 0),
                equip("gp", "지열순환펌프", 2, 0, 0),
                equip("lift", "승강기", 8, 2, 1)
        ));
        return map;
    }

    private static SituationTimeSeriesPointResponse point(String t, int v) {
        return SituationTimeSeriesPointResponse.builder().t(t).v(v).build();
    }

    private static SituationFireDeviceResponse fireDevice(String id, String label, int count) {
        return SituationFireDeviceResponse.builder().id(id).label(label).count(count).build();
    }

    private static SituationAccessItemResponse access(String key, String label, int value) {
        return SituationAccessItemResponse.builder().key(key).label(label).value(value).build();
    }

    private static SituationMeteringUtilityResponse utility(String id, String label, int measuring, int disconnected) {
        return SituationMeteringUtilityResponse.builder()
                .id(id).label(label).measuring(measuring).disconnected(disconnected).build();
    }

    private static SituationLightingModeCountsResponse lightingCounts(int on, int off, int alarm) {
        return SituationLightingModeCountsResponse.builder().on(on).off(off).alarm(alarm).build();
    }

    private static SituationRenewEnergyPointResponse renew(String t, int solar, int geo) {
        return SituationRenewEnergyPointResponse.builder().t(t).solar(solar).geo(geo).build();
    }

    private static SituationMechanicalEquipmentResponse equip(String id, String name, int run, int stop, int alarm) {
        return SituationMechanicalEquipmentResponse.builder()
                .id(id).name(name).run(run).stop(stop).alarm(alarm).build();
    }
}
