package com.solaria.messenger.core.helper;

import com.solaria.messenger.core.model.Message;

/** Mensagem confirmada e indicação de alteração efetiva para publicação. */
public record MessageMutationResult(Message message, boolean changed) { }
