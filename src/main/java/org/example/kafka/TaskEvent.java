package org.example.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskEvent {

    private String eventType;

    private Long taskId;

    private String title;

    private Long assigneeId;
}