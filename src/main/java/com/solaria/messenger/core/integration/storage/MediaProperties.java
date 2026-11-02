package com.solaria.messenger.core.integration.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Propriedades do armazenamento temporário de mídia; sem {@code bucket} as rotas de mídia respondem 503 e o resto do
 * serviço segue normal.
 *
 * @param bucket nome do bucket onde o arquivo espera a retirada.
 * @param ttl por quanto tempo o arquivo fica disponível, contado do depósito.
 * @param uploadValidity validade da permissão de envio.
 * @param downloadValidity validade do link de retirada, criado só na hora de baixar.
 * @param imageMax tamanho máximo de uma imagem.
 * @param audioMax tamanho máximo de um áudio.
 * @param videoMax tamanho máximo de um vídeo.
 * @param fileMax tamanho máximo de um documento ou arquivo qualquer.
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        String bucket,
        @DefaultValue("P7D") Duration ttl,
        @DefaultValue("PT15M") Duration uploadValidity,
        @DefaultValue("PT10M") Duration downloadValidity,
        @DefaultValue("10MB") DataSize imageMax,
        @DefaultValue("16MB") DataSize audioMax,
        @DefaultValue("64MB") DataSize videoMax,
        @DefaultValue("25MB") DataSize fileMax) {
}
