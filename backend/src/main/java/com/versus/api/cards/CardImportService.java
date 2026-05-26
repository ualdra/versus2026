package com.versus.api.cards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.versus.api.cards.domain.Card;
import com.versus.api.cards.dto.CardImportRequest;
import com.versus.api.cards.dto.CardImportResponse;
import com.versus.api.cards.repo.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CardImportService {

    private static final Map<String, String> UNIT_MAP = Map.ofEntries(
            Map.entry("Bota de oro", "goles"),
            Map.entry("Goleadores de la Copa Mundial", "goles"),
            Map.entry("Goles", "goles"),
            Map.entry("Partidos", "partidos"),
            Map.entry("Partidos Jugados", "partidos"),
            Map.entry("Promedio", "goles/partido"),
            Map.entry("Promedio de Goles", "goles/partido"),
            Map.entry("beneficio", "USD"),
            Map.entry("TikTok", "seguidores"),
            Map.entry("YouTube", "seguidores"),
            Map.entry("Twitch", "seguidores"),
            Map.entry("Población en 2026", "habitantes"),
            Map.entry("Agua usada al año", "m³/año"),
            Map.entry("Copa de la Liga", "títulos"),
            Map.entry("Copa del Rey", "títulos"),
            Map.entry("La Liga", "títulos"),
            Map.entry("Supercopa de España", "títulos"),
            Map.entry("Total Nacionales", "títulos"),
            Map.entry("ranking", "puesto"),
            Map.entry("Posicion", "puesto"),
            Map.entry("Ranking en Población en 2026", "puesto")
    );

    private static final Set<String> INVERSE_SUBCATS = Set.of(
            "ranking",
            "Posicion",
            "Ranking en Población en 2026"
    );

    private static final Set<String> NOT_ELIGIBLE_SUBCATS = Set.of(
            "Datos Globales"
    );

    @Value("${versus.cards.json-path:scraper/output/normalized/all.json}")
    private String defaultJsonPath;

    private final CardRepository cardRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public CardImportResponse importFrom(CardImportRequest request) {
        String path = (request.path() != null && !request.path().isBlank())
                ? request.path()
                : defaultJsonPath;

        if (request.purgeFirst()) {
            cardRepo.deleteAll();
        }

        List<String> errors = new ArrayList<>();
        int totalRead = 0;
        int inserted = 0;
        int skippedDuplicates = 0;
        int skippedFiltered = 0;

        try {
            JsonNode root = objectMapper.readTree(Path.of(path).toFile());
            if (!root.isArray()) {
                errors.add("Root JSON element is not an array");
                return new CardImportResponse(0, 0, 0, 0, errors);
            }

            for (JsonNode node : root) {
                totalRead++;
                try {
                    String categoria = nonBlank(node, "categoria");
                    String subcategoria = nonBlank(node, "subcategoria");
                    String nombre = nonBlank(node, "carta");
                    JsonNode valorNode = node.get("valor");
                    if (valorNode == null || valorNode.isNull()) {
                        errors.add("Item #" + totalRead + ": missing 'valor'");
                        skippedFiltered++;
                        continue;
                    }
                    BigDecimal valor = new BigDecimal(valorNode.asText());

                    String hash = sha256(categoria + "|" + subcategoria + "|" + nombre);
                    if (cardRepo.existsByTextHash(hash)) {
                        skippedDuplicates++;
                        continue;
                    }

                    Card card = Card.builder()
                            .categoria(categoria)
                            .subcategoria(subcategoria)
                            .nombre(nombre)
                            .valor(valor)
                            .unidad(UNIT_MAP.get(subcategoria))
                            .inverse(INVERSE_SUBCATS.contains(subcategoria))
                            .eligibleForSurvival(!NOT_ELIGIBLE_SUBCATS.contains(subcategoria))
                            .status(CardStatus.ACTIVE)
                            .scrapedAt(Instant.now())
                            .textHash(hash)
                            .build();
                    cardRepo.save(card);
                    inserted++;
                } catch (Exception e) {
                    errors.add("Item #" + totalRead + ": " + e.getMessage());
                    skippedFiltered++;
                }
            }
        } catch (Exception e) {
            errors.add("Failed to read file: " + e.getMessage());
        }

        return new CardImportResponse(totalRead, inserted, skippedDuplicates, skippedFiltered, errors);
    }

    private static String nonBlank(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull() || n.asText().isBlank()) {
            throw new IllegalArgumentException("Missing or blank field: " + field);
        }
        return n.asText().trim();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
