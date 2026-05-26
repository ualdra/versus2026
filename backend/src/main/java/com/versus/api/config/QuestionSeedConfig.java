package com.versus.api.config;

import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import com.versus.api.questions.repo.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Instant;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class QuestionSeedConfig {

    private final QuestionRepository questions;

    @Bean
    CommandLineRunner seedQuestionsIfEmpty() {
        return args -> {
            seedBinaryIfEmpty();
            seedNumericIfEmpty();
        };
    }

    private void seedBinaryIfEmpty() {
        if (questions.countByStatusAndType(QuestionStatus.ACTIVE, QuestionType.BINARY) > 0) {
            return;
        }
        log.info("No ACTIVE BINARY questions found — seeding default catalogue");
        binary("football", "Who has won more Ballon d'Or awards?", "Lionel Messi", "Cristiano Ronaldo");
        binary("football", "Which club has more UEFA Champions League titles?", "Real Madrid", "Manchester United");
        binary("football", "Who scored more official goals for Brazil?", "Pele", "Ronaldo Nazario");
        binary("football", "Which country has more FIFA World Cup titles?", "Brazil", "Argentina");
        binary("football", "Who has more Premier League titles?", "Manchester United", "Liverpool");

        binary("geography", "Which country has the larger population?", "India", "Canada");
        binary("geography", "Which river is longer?", "Nile", "Thames");
        binary("geography", "Which mountain is higher?", "Mount Everest", "Mont Blanc");
        binary("geography", "Which country is larger by area?", "Russia", "Spain");
        binary("geography", "Which city is farther north?", "Oslo", "Rome");

        binary("cinema", "Which film won more Oscars?", "The Lord of the Rings: The Return of the King", "Titanic");
        binary("cinema", "Who directed Jurassic Park?", "Steven Spielberg", "James Cameron");
        binary("cinema", "Which franchise released its first film earlier?", "Star Wars", "Harry Potter");
        binary("cinema", "Which actor played Iron Man in the MCU?", "Robert Downey Jr.", "Chris Evans");
        binary("cinema", "Which movie has the longer runtime?", "The Godfather Part II", "Toy Story");
    }

    private void seedNumericIfEmpty() {
        if (questions.countByStatusAndType(QuestionStatus.ACTIVE, QuestionType.NUMERIC) > 0) {
            return;
        }
        log.info("No ACTIVE NUMERIC questions found — seeding default catalogue");
        numeric("football", "How many players start on the pitch for one football team?", "players", "11", "0");
        numeric("football", "How many minutes are in standard football regulation time?", "minutes", "90", "0");
        numeric("football", "How many FIFA World Cup titles does Brazil have?", "titles", "5", "0");
        numeric("football", "How many teams played in the 2022 FIFA World Cup?", "teams", "32", "0");

        numeric("geography", "What is the approximate height of Mount Everest?", "meters", "8849", "2");
        numeric("geography", "How many countries are in the European Union?", "countries", "27", "0");
        numeric("geography", "What is the approximate population of Spain?", "millions", "48", "5");

        numeric("cinema", "How many Oscars did Titanic win?", "Oscars", "11", "0");
        numeric("cinema", "What year was the first Star Wars film released?", "year", "1977", "0");
        numeric("cinema", "What is the approximate runtime of The Godfather?", "minutes", "175", "5");
    }

    private void binary(String category, String text, String correct, String incorrect) {
        Question question = baseQuestion(category, text, QuestionType.BINARY);
        question.getOptions().add(QuestionOption.builder()
                .question(question)
                .text(correct)
                .isCorrect(true)
                .build());
        question.getOptions().add(QuestionOption.builder()
                .question(question)
                .text(incorrect)
                .isCorrect(false)
                .build());
        questions.save(question);
    }

    private void numeric(String category, String text, String unit, String correctValue, String tolerancePercent) {
        Question question = baseQuestion(category, text, QuestionType.NUMERIC);
        question.setUnit(unit);
        question.setCorrectValue(new BigDecimal(correctValue));
        question.setTolerancePercent(new BigDecimal(tolerancePercent));
        questions.save(question);
    }

    private Question baseQuestion(String category, String text, QuestionType type) {
        return Question.builder()
                .text(text)
                .type(type)
                .category(category)
                .sourceUrl("seed://default")
                .scrapedAt(Instant.now())
                .status(QuestionStatus.ACTIVE)
                .build();
    }
}
