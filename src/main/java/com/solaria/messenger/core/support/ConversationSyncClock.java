package com.solaria.messenger.core.support;

import java.util.UUID;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Época e versão monotônica da sincronização de conversas.
 *
 * <p>
 * São compartilhadas por REST e eventos e por todos os pods.
 * </p>
 */
@Component
public class ConversationSyncClock {

    private static final String COLLECTION = "sync_clock";
    private static final Query CLOCK = Query.query(Criteria.where("_id").is("conversation-sync"));

    private final MongoTemplate mongo;
    private volatile String epoch;

    public ConversationSyncClock(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public String epoch() {
        if (epoch == null) advance(0);
        return epoch;
    }

    public long nextVersion() {
        return advance(1);
    }

    /** Versão atual, sem avançar o relógio. */
    public long currentVersion() {
        return advance(0);
    }

    private long advance(int step) {
        Update update = new Update().inc("version", step).setOnInsert("epoch", UUID.randomUUID().toString());
        Document clock = mongo.findAndModify(CLOCK, update, FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class, COLLECTION);
        epoch = clock.getString("epoch");
        return clock.get("version", Number.class).longValue();
    }
}
