package com.frietsync.backend.dto.project;

import lombok.Data;

import java.util.UUID;

@Data
public class LabelResponse {
    private UUID id;
    private String name;
    private String color;
}
