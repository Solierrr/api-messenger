package com.solaria.messenger.core.contract.request;


/** Distingue campo omitido de campo explicitamente nulo no PATCH. */
public record PatchField<T>(boolean present, T value) {
    /** Campo omitido no PATCH, distinto de um campo enviado como {@code null}. */
    public static <T> PatchField<T> absent() {
        return new PatchField<>(false, null);
    }
}
