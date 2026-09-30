package com.solaria.messenger.core.model.enums;

/**
 * Situação de uma avaliação ({@link com.solaria.messenger.core.model.Rating}), alterada pelo endpoint de
 * status de {@code /messaging/ratings}.
 */
public enum RatingStatus {
    ACTIVE,
    EXCLUDED,
    DOWN
}
