package com.versus.api.achievements;

import java.util.List;

public final class AchievementCatalog {

    public static final String FIRST_GAME = "first_game";
    public static final String FIRST_WIN = "first_win";
    public static final String STREAK_5 = "streak_5";
    public static final String STREAK_10 = "streak_10";
    public static final String STREAK_20 = "streak_20";
    public static final String PRECISION_FIRST_GAME = "precision_first_game";
    public static final String PRECISION_SNIPER = "precision_sniper";
    public static final String PRECISION_AVG_UNDER_5 = "precision_avg_under_5";
    public static final String SURVIVAL_FIRST_GAME = "survival_first_game";
    public static final String SURVIVAL_10_ROUNDS = "survival_10_rounds";
    public static final String SURVIVAL_PERFECT_LIVES = "survival_perfect_lives";
    public static final String PVP_FIRST_WIN = "pvp_first_win";
    public static final String PVP_10_DUELS = "pvp_10_duels";
    public static final String SABOTAGE_WIN = "sabotage_win";
    public static final String SOCIAL_3_FRIENDS = "social_3_friends";
    public static final String SOCIAL_INVITE = "social_invite";
    public static final String COLLECTOR_ALL_MODES = "collector_all_modes";

    private AchievementCatalog() {
    }

    public static List<Seed> seeds() {
        return List.of(
                new Seed(FIRST_GAME, "Primeros pasos", "Juega tu primera partida.", "first", "Primeros pasos"),
                new Seed(FIRST_WIN, "Primera victoria", "Gana tu primera partida.", "win", "Primeros pasos"),
                new Seed(STREAK_5, "Racha x5", "Consigue una racha de 5 respuestas correctas.", "streak5", "Racha"),
                new Seed(STREAK_10, "Racha x10", "Consigue una racha de 10 respuestas correctas.", "streak10", "Racha"),
                new Seed(STREAK_20, "Racha x20", "Consigue una racha de 20 respuestas correctas.", "streak20", "Racha"),
                new Seed(PRECISION_FIRST_GAME, "Pulso firme", "Juega tu primera partida de precision.", "precision", "Precision"),
                new Seed(PRECISION_SNIPER, "Francotirador", "Termina una partida de precision con desviacion media de 1% o menos.", "sniper", "Precision"),
                new Seed(PRECISION_AVG_UNDER_5, "Cirujano", "Mantiene desviacion media menor de 5% tras 10 partidas de precision.", "target", "Precision"),
                new Seed(SURVIVAL_FIRST_GAME, "Superviviente", "Juega tu primera partida de supervivencia.", "survival", "Supervivencia"),
                new Seed(SURVIVAL_10_ROUNDS, "No caigo", "Alcanza 10 rondas en supervivencia.", "shield", "Supervivencia"),
                new Seed(SURVIVAL_PERFECT_LIVES, "Intocable", "Termina supervivencia con las 3 vidas.", "perfect", "Supervivencia"),
                new Seed(PVP_FIRST_WIN, "Duelista", "Gana tu primer duelo PvP.", "duel", "Multijugador"),
                new Seed(PVP_10_DUELS, "Arena tomada", "Gana 10 duelos PvP.", "arena", "Multijugador"),
                new Seed(SABOTAGE_WIN, "Saboteador", "Gana una partida de sabotaje.", "sabotage", "Multijugador"),
                new Seed(SOCIAL_3_FRIENDS, "Circulo", "Anade 3 amigos.", "friends", "Social"),
                new Seed(SOCIAL_INVITE, "Anfitrion", "Invita a alguien a una partida.", "invite", "Social"),
                new Seed(COLLECTOR_ALL_MODES, "Coleccionista", "Juega todos los modos al menos una vez.", "collector", "Coleccionista")
        );
    }

    public record Seed(String key, String name, String description, String iconKey, String category) {
    }
}
