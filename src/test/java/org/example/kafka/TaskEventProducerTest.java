package org.example.kafka;

import org.example.kafka.TaskEvent;
import org.example.kafka.TaskEventProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaskEventProducerTest {

    @Mock
    private KafkaTemplate<String, TaskEvent> kafkaTemplate;

    @InjectMocks
    private TaskEventProducer taskEventProducer;

    @Test
    void sendTaskCreatedEvent_shouldSendCorrectKafkaMessage() {
        taskEventProducer.sendTaskCreatedEvent(15L, "Create integration test");

        ArgumentCaptor<TaskEvent> eventCaptor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(kafkaTemplate).send(
                eq(TaskEventProducer.TOPIC),
                eq("15"),
                eventCaptor.capture()
        );

        TaskEvent event = eventCaptor.getValue();
        assertEquals("TASK_CREATED", event.getEventType());
        assertEquals(15L, event.getTaskId());
        assertEquals("Create integration test", event.getTitle());
        assertNull(event.getAssigneeId());
    }

    @Test
    void sendTaskAssignedEvent_shouldSendCorrectKafkaMessage() {
        taskEventProducer.sendTaskAssignedEvent(15L, "Create integration test", 6L);

        ArgumentCaptor<TaskEvent> eventCaptor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(kafkaTemplate).send(
                eq(TaskEventProducer.TOPIC),
                eq("15"),
                eventCaptor.capture()
        );

        TaskEvent event = eventCaptor.getValue();
        assertEquals("TASK_ASSIGNED", event.getEventType());
        assertEquals(15L, event.getTaskId());
        assertEquals("Create integration test", event.getTitle());
        assertEquals(6L, event.getAssigneeId());
    }
}
