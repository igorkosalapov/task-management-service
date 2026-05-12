package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.entity.TaskStatus;

@Getter
@AllArgsConstructor
public class TaskResponse {

    private Long id;

    private String title;

    private String description;

    private TaskStatus status;

    private Long assigneeId;

    private String assigneeName;
}
