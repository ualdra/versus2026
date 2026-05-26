package com.versus.api.it.support;

import com.versus.api.cards.CardStatus;
import com.versus.api.cards.domain.Card;
import com.versus.api.cards.repo.CardRepository;
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
    private final CardRepository cards;
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
                .isActive(true)
                .enabled(true);
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

    /**
     * Par determinista para Survival (binary): misma categoría/subcategoría,
     * no-inverso, valor 100 y 50. CardService elegirá uno como "A" y buscará un
     * partner; con ambos elegibles, siempre encuentra el par y la carta de
     * valor=100 es el winner.
     */
    public CardPair binaryCardPair() {
        int n = seq.incrementAndGet();
        Card a = saveCard("sport", "football", "Card A #" + n, new BigDecimal("100"), n + "-a");
        Card b = saveCard("sport", "football", "Card B #" + n, new BigDecimal("50"), n + "-b");
        return new CardPair(a, b);
    }

    /** Carta numérica con valor=100 para Precision. */
    public Card numericCard() {
        int n = seq.incrementAndGet();
        return saveCard("sport", "football", "Num Card #" + n, new BigDecimal("100"), n + "-num");
    }

    private Card saveCard(String categoria, String subcategoria, String nombre,
                          BigDecimal valor, String tag) {
        return cards.save(Card.builder()
                .categoria(categoria).subcategoria(subcategoria).nombre(nombre)
                .valor(valor).unidad("pts").inverse(false).eligibleForSurvival(true)
                .status(CardStatus.ACTIVE)
                .textHash("hash-card-" + tag + "-" + UUID.randomUUID())
                .build());
    }

    public record CardPair(Card a, Card b) {
        /** Ganador para survival no-inverso: el de mayor valor. */
        public Card winner() {
            return a.getValor().compareTo(b.getValor()) >= 0 ? a : b;
        }
        public Card loser() {
            return winner() == a ? b : a;
        }
    }
}
