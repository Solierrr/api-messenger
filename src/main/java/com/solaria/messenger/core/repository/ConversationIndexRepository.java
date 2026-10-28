package com.solaria.messenger.core.repository;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ConversationUserState;

import jakarta.annotation.PostConstruct;

/** Índices e coleções precisam estar prontos antes da primeira transação de admissão, sem DDL dentro dela. */
@Repository
public class ConversationIndexRepository {

    private static final String LEGACY_DIRECT_INDEX = "uq_direct_active_direct_key";
    private final MongoTemplate mongo;

    public ConversationIndexRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Cria a unicidade nova da direta antes de remover o índice legado, e garante o índice de estados pessoais. */
    @PostConstruct
    public void prepareIndexes() {
        var indexes = mongo.indexOps(Conversation.class);
        List<IndexInfo> existing = mongo.collectionExists(Conversation.class) ? indexes.getIndexInfo() : List.of();
        var old = existing.stream().filter(index -> LEGACY_DIRECT_INDEX.equals(index.getName())).findFirst();
        if (old.isPresent() && (!old.get().isUnique() || old.get().getIndexFields().size() != 1
                || !"direct_key".equals(old.get().getIndexFields().getFirst().getKey()))) {
            throw new IllegalStateException("O índice legado de direta tem uma definição inesperada.");
        }
        // Criar a restrição nova primeiro: duplicatas existentes impedem iniciar, sem excluir ou mesclar dados.
        indexes.createIndex(new Index().on("direct_key", Sort.Direction.ASC).unique().named("uq_direct_pair")
                .partial(PartialIndexFilter.of(Criteria.where("conversation_type").is("DIRECT"))));
        if (old.isPresent()) indexes.dropIndex(LEGACY_DIRECT_INDEX);
        mongo.indexOps(ConversationUserState.class).createIndex(new Index()
                .on("conversation_id", Sort.Direction.ASC).on("user_id", Sort.Direction.ASC)
                .unique().named("uq_conversation_user_state"));
    }
}
