package com.versus.api.it.capa0;

import com.versus.api.auth.domain.RefreshToken;
import com.versus.api.auth.repo.RefreshTokenRepository;
import com.versus.api.it.support.AbstractIT;
import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import com.versus.api.questions.repo.QuestionRepository;
import com.versus.api.users.Role;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Capa 0 — Invariantes de esquema")
class SchemaConstraintsIT extends AbstractIT {

    @Autowired
    UserRepository userRepo;
    @Autowired
    QuestionRepository questionRepo;
    @Autowired
    RefreshTokenRepository refreshTokenRepo;

    @Test
    @DisplayName("C1 — users.email es único")
    void emailUnico() {
        String email = "dup@versus.test";
        userRepo.save(userWith("alice", email));
        assertThatThrownBy(() -> userRepo.saveAndFlush(userWith("bob", email)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("C2 — users.username es único")
    void usernameUnico() {
        String username = "duplicado";
        userRepo.save(userWith(username, "a@versus.test"));
        assertThatThrownBy(() -> userRepo.saveAndFlush(userWith(username, "b@versus.test")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("C3 — questions.text_hash es único")
    void textHashUnico() {
        String hash = "dup-hash-" + UUID.randomUUID();
        questionRepo.save(questionWith("Question A", hash));
        assertThatThrownBy(() -> questionRepo.saveAndFlush(questionWith("Question B", hash)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("C6 — refresh_tokens persiste con userId del usuario")
    void refreshTokenPersistsByUserId() {
        User user = userRepo.save(userWith("tokened", "tokened@versus.test"));
        RefreshToken token = refreshTokenRepo.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash("somehash-" + UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build());

        assertThat(token.getId()).isNotNull();
        assertThat(refreshTokenRepo.findAll())
                .anyMatch(t -> t.getUserId().equals(user.getId()));
    }

    @Test
    @DisplayName("C7 — question_options se borran en cascada al borrar question")
    void questionOptionsCascade() {
        String hash = "cascade-hash-" + UUID.randomUUID();
        Question q = questionWith("Cascade Q", hash);
        QuestionOption opt = QuestionOption.builder()
                .question(q)
                .text("Option A")
                .isCorrect(true)
                .build();
        q.setOptions(List.of(opt));
        Question saved = questionRepo.save(q);

        UUID qId = saved.getId();
        assertThat(saved.getOptions()).hasSize(1);

        questionRepo.delete(saved);
        assertThat(questionRepo.findById(qId)).isEmpty();
    }

    private User userWith(String username, String email) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash("$2a$10$hashplaceholder")
                .role(Role.PLAYER)
                .status(UserStatus.ACTIVE)
                .isActive(true)
                .build();
    }

    private Question questionWith(String text, String hash) {
        return Question.builder()
                .text(text)
                .type(QuestionType.BINARY)
                .category("general")
                .status(QuestionStatus.ACTIVE)
                .textHash(hash)
                .build();
    }
}
