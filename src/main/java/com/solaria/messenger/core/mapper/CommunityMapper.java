package com.solaria.messenger.core.mapper;

import java.util.List;

import com.solaria.messenger.core.contract.response.CommunityMemberDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.model.ProjectCommunity;

/** Mapeamento único da entidade persistida para o contrato público, sem consultas ou autorização. */
public final class CommunityMapper {
    private CommunityMapper() { }

    /** Converte a comunidade e seus membros embutidos no contrato de resposta. */
    public static CommunityResponseDTO toResponse(ProjectCommunity community) {
        List<CommunityMemberDTO> members = community.getMembers() == null ?
                List.of() : 
                community.getMembers().stream()
                        .map(m -> CommunityMemberDTO.builder()
                        .userId(m.getUserId())
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt())
                        .build())
                        .toList();

        return CommunityResponseDTO.builder()
                .id(community.getId())
                .projectId(community.getProjectId())
                .members(members)
                .status(community.getStatus())
                .createdBy(community.getCreatedBy())
                .createdAt(community.getCreatedAt())
                .updatedAt(community.getUpdatedAt())
                .build();
    }
}
