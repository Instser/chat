package com.instser.chat.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class DeleteSessionRequest {
    private List<String> sessionIds;
}