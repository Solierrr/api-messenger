package com.solaria.messenger.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageMetadata {

    private String turnId;

    private Boolean contentAnonymized;

    private List<String> specialistsUsed;

    private List<String> workflowSteps;
}
