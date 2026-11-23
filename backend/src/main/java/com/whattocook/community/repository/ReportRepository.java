package com.whattocook.community.repository;

import com.whattocook.community.domain.Report;
import com.whattocook.community.domain.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    long countByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);
}
