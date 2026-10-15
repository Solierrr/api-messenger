package com.solaria.messenger.model.enums;

/**
 * Classificação NPS a partir da nota de 0 a 10.
 *
 * <ul>
 *   <li>{@code DETRACTOR} -> 0 a 6</li>
 *   <li>{@code PASSIVE} -> 7 e 8</li>
 *   <li>{@code PROMOTER} -> 9 e 10</li>
 * </ul>
 */
public enum NpsClassification {
    DETRACTOR,
    PASSIVE,
    PROMOTER;

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 10;

    public static NpsClassification fromScore(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("A nota NPS deve estar entre 0 e 10.");
        }
        if (score >= 9) {
            return PROMOTER;
        }
        if (score >= 7) {
            return PASSIVE;
        }
        return DETRACTOR;
    }
}
