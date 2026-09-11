package com.mpole.hana.digitaltwin.memo.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

/**
 * 운영 메모 — 임시 기록용 메모장.
 *
 * <p>업무 도메인과 무관한 <b>부록 기능</b>이다. 작업 중 메모를 남길 곳이 필요해 만들었으며,
 * 다른 도메인이 이 테이블을 참조하지 않는다(FK 없음).</p>
 *
 * <p>일시는 {@link DateEntity} 의 {@code created_at}/{@code updated_at} 을 그대로 쓴다.</p>
 */
@Entity
@Table(name = "tbl_memo",
        indexes = {
                @Index(name = "idx_memo_updated", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Memo extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memo_id")
    private Long memoId;

    @Column(name = "title", nullable = false, length = 200)
    @Comment("메모 제목")
    private String title;

    /** 길이 제한을 두지 않는다 — 메모장 용도라 얼마나 길어질지 모른다. */
    @Column(name = "content", columnDefinition = "TEXT")
    @Comment("메모 본문")
    private String content;
}
