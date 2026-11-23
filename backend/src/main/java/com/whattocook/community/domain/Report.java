package com.whattocook.community.domain;

import com.whattocook.global.common.BaseTimeEntity;
import com.whattocook.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 레시피·댓글 신고. 대상은 종류와 id로 가리킨다. (신고자, 대상) 유니크로 한 사람이 한 번만 신고한다.
 */
@Getter
@Entity
@Table(name = "report", uniqueConstraints = @UniqueConstraint(
        name = "uk_report_reporter_target", columnNames = {"reporter_id", "target_type", "target_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    public Report(User reporter, ReportTargetType targetType, Long targetId) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
    }
}
