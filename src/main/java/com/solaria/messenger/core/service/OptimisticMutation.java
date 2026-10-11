package com.solaria.messenger.core.service;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utilitário de escrita condicional por versão (CAS), usado por conversas e comunidades
 *
 * <ul>
 *     <li>{@link #apply(Object, Function, Supplier, Supplier)} -> aplica a escrita e tenta de novo se a versão mudou.</li>
 *     <li>{@link #nextVersion(Long)} -> próxima versão esperada pelo CAS.</li>
 * </ul>
 */
public final class OptimisticMutation {

    private static final int MAX_ATTEMPTS = 3;

    private OptimisticMutation() { }

    /**
     * Aplica a escrita até 3 vezes, relendo o documento entre as tentativas.
     * A mutação devolve {@code null} quando não há nada a gravar, {@code 1} quando gravou e outro valor quando o CAS não gravou.
     */
    public static <T> T apply(T initial, Function<T, Long> mutation, Supplier<T> reload,
            Supplier<? extends RuntimeException> exhausted) {
        T current = initial;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long modified = mutation.apply(current);
            if (modified == null) return current;

            // relê nos dois casos: gravou (devolve o novo estado) ou perdeu para outra escrita (tenta de novo)
            current = reload.get();
            if (modified == 1) return current;
        }
        throw exhausted.get();
    }

    /** Próxima versão do CAS; documento antigo sem versão começa em zero */
    public static Long nextVersion(Long version) {
        return version == null ? 0L : Math.addExact(version, 1L);
    }
}
