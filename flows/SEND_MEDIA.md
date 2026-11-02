<p align="center">
  <img src="../assets/readme/banners/springboot.png" alt="Spring Boot" width="100%">
</p>

# Envio de mídia

Acontece quando alguém manda foto, vídeo, áudio ou arquivo. O servidor não guarda a mídia para sempre: ela espera num bucket por 7 dias, só para chegar aos aparelhos de quem participa da conversa, e depois some. O `CreateMediaUploadUseCase` dá a permissão de envio, o `AttachmentHelper` confere o arquivo quando a mensagem o cita, e o `GetMediaContentUseCase` entrega o link de retirada. Os bytes nunca passam pela API.

```mermaid
sequenceDiagram
    participant A as Aparelho do remetente
    participant Msg as api-messenger
    participant S as Bucket (Google Cloud Storage)
    participant B as Aparelho de um participante

    A->>Msg: POST /messaging/media (conversa, tipo, mime, tamanho)
    Msg->>Msg: confere participação, comunidade ativa, tipo e tamanho
    Msg-->>A: mediaId + permissão de envio (15 min)
    A->>S: PUT dos bytes, com os cabeçalhos devolvidos
    A->>Msg: POST /messaging/messages (attachment com mediaId)
    Msg->>S: o arquivo existe, é desta conversa, do remetente, com este tamanho e tipo?
    Msg-->>A: mensagem gravada, com expiresAt e downloadPath
    Msg-->>B: MESSAGE_CREATED com a descrição do anexo (canal .events)
    B->>Msg: GET /messaging/media/{mediaId}/content
    Msg->>Msg: confere participação e prazo
    Msg-->>B: 302 para o link assinado (10 min)
    B->>S: GET dos bytes
    Note over B: grava no aparelho; o servidor não é mais necessário
    Note over B,Msg: numa sessão nova, GET /messaging/media lista o que ainda vale baixar
```

- O envio só vale se o tipo, o formato do mime e o tamanho cabem nas regras: imagem até 10 MB, áudio até 16 MB, vídeo até 64 MB e arquivo até 25 MB.
- O bucket recusa um envio diferente do declarado, porque o tipo, o tamanho exato e o dono entram na assinatura.
- A localização é um anexo sem arquivo: leva só latitude, longitude e rótulo, e não passa pelo bucket.
- A conversa com o assistente e a comunidade arquivada não aceitam anexo novo. Ler continua valendo.
- Quem saiu da conversa perde a retirada.
- Depois dos 7 dias, a retirada responde `410 MEDIA_EXPIRED`.
- Excluir a mensagem apaga também o arquivo.
- O servidor não confirma recebimento nem avisa o remetente de que ninguém baixou.

## Erros

- Tipo, mime ou tamanho fora das regras geram `INVALID_FIELD`.
- Mensagem que cita um arquivo que não está no bucket gera `MEDIA_NOT_UPLOADED`.
- Arquivo de outra conversa, de outro remetente ou com tamanho ou tipo diferentes do declarado gera `MEDIA_MISMATCH`.
- Bucket não configurado ou fora do ar gera `DEPENDENCY_UNAVAILABLE`. Mensagens sem arquivo seguem normais.
