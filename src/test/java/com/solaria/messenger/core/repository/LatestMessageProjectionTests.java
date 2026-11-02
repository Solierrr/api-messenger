package com.solaria.messenger.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.AttachmentType;
import com.solaria.messenger.core.repository.projection.ConversationMessageHead;

/** Cobre o tipo do anexo na última mensagem de cada conversa, que a lista usa no lugar do texto vazio. */
class LatestMessageProjectionTests {

    @Test
    void latestMessageCarriesTheAttachmentTypeOrNone() {
        Message audio = message("c1", 4, AttachmentType.AUDIO);
        Message text = message("c2", 7, null);
        MessageRepository repository = mock(MessageRepository.class);
        given(repository.aggregateLatestMessages(any())).willReturn(List.of(audio, text));
        given(repository.findLatestByConversationIds(any())).willCallRealMethod();

        Map<String, ConversationMessageHead> heads = repository.findLatestByConversationIds(Set.of("c1", "c2"));

        assertThat(heads.get("c1").lastMessage().attachmentType()).isEqualTo(AttachmentType.AUDIO);
        assertThat(heads.get("c2").lastMessage().attachmentType()).isNull();
    }

    private static Message message(String conversationId, int sequence, AttachmentType type) {
        Message message = new Message();
        message.setId("m" + sequence);
        message.setConversationId(conversationId);
        message.setSequence(sequence);
        if (type != null) {
            Attachment attachment = new Attachment();
            attachment.setType(type);
            message.setAttachment(attachment);
        }
        return message;
    }
}
