package com.solaria.messenger.core.helper;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.integration.storage.MediaProperties;
import com.solaria.messenger.core.model.enums.AttachmentType;

/**
 * Regras do que o servidor aceita receber como arquivo: tipo, formato do mime e tamanho máximo por categoria.
 *
 * <p>
 * Só confere o que o servidor precisa para se proteger e para cobrar o armazenamento; converter, comprimir e escolher
 * o formato é do aparelho.
 * </p>
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #requireAllowed(AttachmentType, String, long)} -> recusa tipo, mime ou tamanho fora das regras.</li>
 *     <li>{@link #maxBytes(AttachmentType)} -> tamanho máximo da categoria.</li>
 *     <li>{@link #normalizeMime(String)} -> mime sem parâmetros e em minúsculas.</li>
 * </ul>
 */
@Component
public class MediaRulesHelper {

    private static final Set<String> IMAGE_MIMES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> AUDIO_MIMES = Set.of("audio/mp4", "audio/aac", "audio/mpeg", "audio/ogg", "audio/webm");
    private static final Set<String> VIDEO_MIMES = Set.of("video/mp4", "video/quicktime", "video/webm");
    private static final Pattern MIME_FORMAT = Pattern.compile("^[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*$");

    private final MediaProperties properties;

    public MediaRulesHelper(MediaProperties properties) {
        this.properties = properties;
    }

    /**
     * Confere se o arquivo declarado pode ser depositado.
     *
     * @param type categoria do anexo.
     * @param mimeType tipo do arquivo, com ou sem parâmetros.
     * @param sizeBytes tamanho declarado, maior que zero e até o máximo da categoria.
     * @throws InvalidFieldException caso a categoria não tenha arquivo, o mime não combine ou o tamanho passe do limite.
     */
    public void requireAllowed(AttachmentType type, String mimeType, long sizeBytes) {
        if (!type.hasFile()) throw new InvalidFieldException("O tipo " + type + " não tem arquivo para depositar.");
        String mime = normalizeMime(mimeType);
        if (!MIME_FORMAT.matcher(mime).matches()) throw new InvalidFieldException("mimeType deve ter o formato tipo/subtipo.");
        Set<String> allowed = switch (type) {
            case IMAGE -> IMAGE_MIMES;
            case AUDIO -> AUDIO_MIMES;
            case VIDEO -> VIDEO_MIMES;
            default -> null;
        };
        if (allowed != null && !allowed.contains(mime)) {
            throw new InvalidFieldException("mimeType " + mime + " não é aceito para " + type + ".");
        }
        if (sizeBytes <= 0) throw new InvalidFieldException("sizeBytes deve ser maior que zero.");
        if (sizeBytes > maxBytes(type)) {
            throw new InvalidFieldException("O tamanho passa do máximo de " + maxBytes(type) + " bytes para " + type + ".");
        }
    }

    /** Tamanho máximo, em bytes, de um arquivo da categoria. */
    public long maxBytes(AttachmentType type) {
        return switch (type) {
            case IMAGE -> properties.imageMax().toBytes();
            case AUDIO -> properties.audioMax().toBytes();
            case VIDEO -> properties.videoMax().toBytes();
            case FILE -> properties.fileMax().toBytes();
            case LOCATION -> 0;
        };
    }

    /** Mime sem parâmetros (o que vem depois de ';') e em minúsculas; vazio quando falta. */
    public static String normalizeMime(String mimeType) {
        if (mimeType == null) return "";
        int parameters = mimeType.indexOf(';');
        String bare = parameters < 0 ? mimeType : mimeType.substring(0, parameters);
        return bare.strip().toLowerCase(Locale.ROOT);
    }
}
