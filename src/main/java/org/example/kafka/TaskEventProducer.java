package org.example.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskEventProducer {

    public static final String TOPIC = "task-events";

    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;

    public void sendTaskCreatedEvent(Long taskId, String title) {
        TaskEvent event = new TaskEvent(
                "TASK_CREATED",
                taskId,
                title,
                null
        );

        kafkaTemplate.send(TOPIC, String.valueOf(taskId), event);
    }

    public void sendTaskAssignedEvent(Long taskId, String title, Long assigneeId) {
        TaskEvent event = new TaskEvent(
                "TASK_ASSIGNED",
                taskId,
                title,
                assigneeId
        );

        kafkaTemplate.send(TOPIC, String.valueOf(taskId), event);
    }
}