package com.whattocook.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whattocook.global.config.JpaConfig;
import com.whattocook.support.TestcontainersConfiguration;
import com.whattocook.user.domain.User;
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
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("이메일로 사용자를 조회하고 생성·수정 시각이 기록된다")
    void findByEmail() {
        userRepository.saveAndFlush(new User("chef@example.com", "hashed", "요리사"));

        assertThat(userRepository.existsByEmail("chef@example.com")).isTrue();
        assertThat(userRepository.findByEmail("chef@example.com"))
                .hasValueSatisfying(user -> {
                    assertThat(user.getNickname()).isEqualTo("요리사");
                    assertThat(user.getCreatedAt()).isNotNull();
                });
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    @DisplayName("같은 이메일은 DB 유니크 제약으로 막힌다")
    void uniqueEmail() {
        userRepository.saveAndFlush(new User("chef@example.com", "hashed", "요리사"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("chef@example.com", "hashed", "다른 요리사")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
