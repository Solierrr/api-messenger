package com.solaria.messenger.core.service.message;

import com.solaria.messenger.core.model.Message;

/** Mensagem confirmada e indicação de alteração efetiva para publicação. */
record MessageMutationResult(Message message, boolean changed) { }
