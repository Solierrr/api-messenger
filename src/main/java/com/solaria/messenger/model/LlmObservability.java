package com.solaria.messenger.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.model.enums.ObservabilityStepType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "llm_observability")
public class LlmObservability {

    @Id
    private String id;

    @Indexed
    @Field("node")
    private String node;

    @Field("step_order")
    private Integer stepOrder;

    @Field("step_type")
    private ObservabilityStepType stepType;

    @Field("model")
    private String model;

    @Indexed
    @Field("conversation_id")
    private String conversationId;

    @Field("tokens_in")
    private Integer tokensIn;

    @Field("tokens_out")
    private Integer tokensOut;

    @Field("tokens_total")
    private Integer tokensTotal;

    @Field("latency_ms")
    private Double latencyMs;

    private Boolean  status;

    private String error;

    private Instant timestamp;
}
