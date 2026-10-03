package com.solaria.messenger.core.repository.projection;


/** Cabeça persistida e preview da conversa, sem estado pessoal */
public record ConversationMessageHead(int persistedHeadSequence, LatestMessage lastMessage) { }
