package com.solaria.messenger.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.Environment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponseDTO {

    private String id;
    private ConversationType conversationType;
    private Set<UUID> participantIds;
    private UUID createdBy;
    private String title;
    private String communityId;
    private Environment environment;
    private String userType;
    private Map<String, Object> userDetails;
    private ConversationStatus status;
    private Instant startedAt;
    private Instant lastInteractionAt;
}
