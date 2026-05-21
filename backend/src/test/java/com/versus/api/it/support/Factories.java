package com.versus.api.it.support;

import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import com.versus.api.questions.repo.QuestionRepository;
import com.versus.api.users.Role;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Component
@Profile("it")
@RequiredArgsConstructor
public class Factories {

    public static final String DEFAULT_PASSWORD = "Test1234!";

    private final UserRepository users;
    private final QuestionRepository questions;
    private final PasswordEncoder encoder;
    private final AtomicInteger seq = new AtomicInteger();

    public User user() {
        return user(b -> {});
    }

    public User admin() {
        return user(b -> b.role(Role.ADMIN));
    }

    public User moderator() {
        return user(b -> b.role(Role.MODERATOR));
    }

    public User user(Consumer<User.UserBuilder> custom) {
        int n = seq.incrementAndGet();
        User.UserBuilder b = User.builder()
                .username("user" + n + "_" + UUID.randomUUID().toString().substring(0, 6))
                .email("user" + n + "_" + UUID.randomUUID().toString().substring(0, 6) + "@versus.test")
                .passwordHash(encoder.encode(DEFAULT_PASSWORD))
                .role(Role.PLAYER)
                .status(UserStatus.ACTIVE)
                .isActive(true);
        custom.accept(b);
        return users.save(b.build());
    }

    public Question binaryQuestion() {
        int n = seq.incrementAndGet();
        Question q = Question.builder()
                .text("Binary question #" + n + " " + UUID.randomUUID())
                .type(QuestionType.BINARY)
                .category("general")
                .status(QuestionStatus.ACTIVE)
                .textHash("hash-bin-" + n + "-" + UUID.randomUUID())
                .build();
        QuestionOption correct = QuestionOption.builder()
                .question(q)
                .text("Yes")
                .isCorrect(true)
                .build();
        QuestionOption wrong = QuestionOption.builder()
                .question(q)
                .text("No")
                .isCorrect(false)
                .build();
        q.setOptions(List.of(correct, wrong));
        return questions.save(q);
    }

    public Question numericQuestion() {
        int n = seq.incrementAndGet();
        return questions.save(Question.builder()
                .text("Numeric question #" + n + " " + UUID.randomUUID())
                .type(QuestionType.NUMERIC)
                .category("general")
                .status(QuestionStatus.ACTIVE)
                .correctValue(new BigDecimal("100"))
                .unit("kg")
                .tolerancePercent(new BigDecimal("5"))
                .textHash("hash-num-" + n + "-" + UUID.randomUUID())
                .build());
    }
}
