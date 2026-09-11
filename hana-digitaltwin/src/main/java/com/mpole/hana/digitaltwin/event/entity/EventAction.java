package com.mpole.hana.digitaltwin.event.entity;

import com.mpole.hana.common.constant.ActionStatus;
import com.mpole.hana.common.constant.NotificationChannel;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 1건에서 실행된 <b>채널별 액션 로그</b>({@code tbl_event_action}) — 1 event : N action.
 *
 * <p>3계층(설정 → 발생 → 발행) 중 <b>발행</b>을 담당한다. 어떤 채널을 켤지는
 * {@link EventRuleLevel#getOutputs()} 가 정하고, 그 결과가 이 행들로 남는다.</p>
 *
 * <h3>관계</h3>
 * <pre>
 *   Event ──1 : N──▶ EventAction (이 클래스)     UNIQUE(event_id, channel)
 *     │                   ├ channel=POPUP  status=SENT → (사람이 확인) DONE
 *     │                   ├ channel=SOP    status=SENT → (Unity 완료 보고) DONE
 *     │                   ├ channel=CCTV   status=SENT
 *     │                   └ channel=SMS    status=PENDING (발송기 미구현)
 *     └ event_id 는 소프트 참조 — FK 를 걸지 않는다
 * </pre>
 *
 * <h3>왜 별도 테이블인가</h3>
 * 한 이벤트가 POPUP+SOP+CCTV+SMS 를 동시에 발행할 수 있고 <b>각 채널이 서로 다른 상태</b>를 갖는다
 * (팝업은 확인됨, SOP 는 진행중, SMS 는 실패). {@code tbl_event} 에 채널별 컬럼을 두면
 * 6채널 × (상태/시각/처리자) = 18컬럼이 되고 채널이 늘 때마다 스키마가 바뀐다.
 * 여기서는 {@link NotificationChannel} enum 값만 추가하면 된다.
 *
 * <h3>왜 event_id 에 FK 가 없는가</h3>
 * 2개월 보관 정책({@code core.purge_events})에서 이벤트와 액션의 삭제 순서·범위를 자유롭게
 * 다루기 위해서다. 정합성은 삭제 함수가 조인으로 함께 지운다.
 *
 * <h3>초기 status 가 채널마다 다른 이유</h3>
 * <ul>
 *   <li><b>POPUP/SOP/CCTV</b> — 웹소켓 발행이 곧 전달이므로 {@code SENT} 로 시작하고,
 *       완료 보고는 클라이언트(Unity/Web)가 한다.</li>
 *   <li><b>SMS/EMAIL/DEVICE_CONTROL</b> — 서버가 보내야 하는데 발송·실행기가 아직 없어
 *       {@code PENDING} 으로 남는다.</li>
 * </ul>
 *
 * <p>SOP 는 서버가 템플릿({@code tbl_sop_template})만 공급하고 진행·완료는 클라이언트가 수행한다.
 * 서버는 클라이언트의 완료 보고를 받아 이 행의 {@code status}/{@code result} 를 갱신할 뿐이다.</p>
 */
@Entity
@Table(
        name = "tbl_event_action",
        uniqueConstraints = {
                // 한 이벤트에 같은 채널은 1건. 재발행은 새 행이 아니라 status 갱신.
                @UniqueConstraint(name = "uk_event_action_channel", columnNames = {"event_id", "channel"})
        },
        indexes = {
                @Index(name = "idx_event_action_event", columnList = "event_id"),
                @Index(name = "idx_event_action_channel", columnList = "channel,status")
        }
)
@Comment("이벤트 채널별 액션 로그 — 한 이벤트에서 발행된 채널마다 1행(3계층 중 '발행')")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventAction extends DateEntity {

    /** UUIDv7 — 생성 측에서 채운다. */
    @Id
    @Column(name = "action_id")
    @Comment("액션 PK(UUIDv7). 발행 시점에 event-server 가 생성한다")
    private UUID actionId;

    /** 소속 이벤트({@code tbl_event.event_id}). 소프트 참조. */
    @Column(name = "event_id", nullable = false)
    @Comment("소속 이벤트 id(tbl_event.event_id). 보관 정책의 자유도를 위해 FK 를 걸지 않은 소프트 참조")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    @Comment("발행 채널(POPUP/SOP/CCTV/SMS/EMAIL/DEVICE_CONTROL). event_id 와 묶여 유니크 — 재발행은 새 행이 아니라 이 행의 status 갱신이다")
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    @Comment("채널별 진행 상태. 클라이언트 채널(POPUP/SOP/CCTV)은 SENT 로 시작해 완료 보고 시 DONE, 서버 채널(SMS/EMAIL/DEVICE_CONTROL)은 발송기 미구현이라 PENDING 으로 남는다")
    private ActionStatus status = ActionStatus.PENDING;

    /** 발행 내용 — SOP templateId, SMS 수신자, 제어 대상/값 등 채널마다 다르다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    @Comment("발행 내용(채널마다 형태가 다름). SOP={templateId}, CCTV={source,limit}, SMS/EMAIL={recipientGroupId,templateId}, DEVICE_CONTROL={commandPointId,value}. POPUP 은 이벤트 본문만으로 표출 가능해 비어 있다")
    private Map<String, Object> payload;

    /** 완료 결과 — SOP 단계별 응답 등 클라이언트가 보고한 내용. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    @Comment("완료 보고 내용. 클라이언트가 완료 API 에 실어 보낸 body 를 그대로 보관한다(SOP 단계별 응답 등)")
    private Map<String, Object> result;

    @Column(name = "issued_at", nullable = false)
    @Comment("발행 시각. 이벤트 발생과 동시에 기록된다")
    private OffsetDateTime issuedAt;

    @Column(name = "completed_at")
    @Comment("완료 시각. null 이면 아직 처리되지 않았다(팝업 미확인, SOP 진행중 등)")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by")
    @Comment("완료 처리한 사용자 id(tbl_user.user_id 소프트 참조). 서버 발송 채널은 null")
    private Long completedBy;

    @Column(name = "error_message", columnDefinition = "text")
    @Comment("실패 사유(status=FAILED 일 때). 외부 전송 오류 등")
    private String errorMessage;
}
