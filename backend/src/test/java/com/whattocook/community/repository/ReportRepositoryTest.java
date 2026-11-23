package com.whattocook.community.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whattocook.community.domain.Report;
import com.whattocook.community.domain.ReportTargetType;
import com.whattocook.global.config.JpaConfig;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
import com.whattocook.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
class ReportRepositoryTest {

    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("대상별로 신고 수를 세고, 신고 여부를 확인한다")
    void countAndExists() {
        User a = userRepository.save(new User("report-a@example.com", "hashed", "에이"));
        User b = userRepository.save(new User("report-b@example.com", "hashed", "비"));
        reportRepository.save(new Report(a, ReportTargetType.RECIPE, 1L));
        reportRepository.save(new Report(b, ReportTargetType.RECIPE, 1L));
        reportRepository.save(new Report(a, ReportTargetType.COMMENT, 1L));

        assertThat(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.RECIPE, 1L)).isEqualTo(2);
        assertThat(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.COMMENT, 1L)).isEqualTo(1);
        assertThat(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(b.getId(), ReportTargetType.COMMENT, 1L))
                .isFalse();
    }

    @Test
    @DisplayName("같은 사용자가 같은 대상을 두 번 신고하면 유니크 제약에 걸린다")
    void uniqueReporterTarget() {
        User a = userRepository.save(new User("report-dup@example.com", "hashed", "중복"));
        reportRepository.saveAndFlush(new Report(a, ReportTargetType.RECIPE, 1L));

        assertThatThrownBy(() -> reportRepository.saveAndFlush(new Report(a, ReportTargetType.RECIPE, 1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
