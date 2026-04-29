package com.versus.api.questions.repo;

import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @Query(value = """
            SELECT * FROM questions
            WHERE status = 'ACTIVE'
              AND (:type IS NULL OR type = :type)
              AND (:category IS NULL OR category = :category)
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Question> findRandomActive(@Param("type") String type, @Param("category") String category);

    @Query("SELECT DISTINCT q.category FROM Question q WHERE q.status = :status AND q.category IS NOT NULL ORDER BY q.category")
    List<String> findDistinctCategories(@Param("status") QuestionStatus status);

    long countByStatusAndType(QuestionStatus status, QuestionType type);
}
