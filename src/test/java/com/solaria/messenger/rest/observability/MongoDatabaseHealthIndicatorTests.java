package com.solaria.messenger.rest.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.MongoTimeoutException;

@ExtendWith(MockitoExtension.class)
class MongoDatabaseHealthIndicatorTests {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MongoDatabaseHealthIndicator indicator;

    @Test
    void ficaUpQuandoBancoDoServicoResponde() {
        given(mongoTemplate.executeCommand("{ hello: 1 }")).willReturn(new Document("maxWireVersion", 25));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("maxWireVersion", 25);
    }

    @Test
    void ficaDownQuandoBancoDoServicoFalha() {
        given(mongoTemplate.executeCommand("{ hello: 1 }")).willThrow(new MongoTimeoutException("sem resposta"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
