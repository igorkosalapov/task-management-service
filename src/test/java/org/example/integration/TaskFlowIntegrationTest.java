package org.example.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.entity.Task;
import org.example.entity.TaskStatus;
import org.example.entity.User;
import org.example.kafka.TaskEvent;
import org.example.kafka.TaskEventProducer;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TaskFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KafkaTemplate<String, TaskEvent> kafkaTemplate;

    @Autowired
    private KafkaContainer kafkaContainer;

    private Consumer<String, TaskEvent> consumer;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        userRepository.deleteAll();

        consumer = createConsumer();

        /*
         * Назначаем partition напрямую, без subscribe().
         * Это исключает групповую ребалансировку во время теста.
         */
        assignConsumerFromEnd(consumer);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close(Duration.ofSeconds(2));
        }
    }

    @Test
    void createTask_shouldPersistTaskAndPublishKafkaEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Kafka integration test",
                                  "description": "Verify HTTP, PostgreSQL and Kafka"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();

        JsonNode responseBody = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        long taskId = responseBody.get("id").asLong();

        Task persistedTask = taskRepository.findById(taskId)
                .orElseThrow();

        assertEquals(
                "Kafka integration test",
                persistedTask.getTitle()
        );
        assertEquals(
                TaskStatus.NEW,
                persistedTask.getStatus()
        );

        ConsumerRecord<String, TaskEvent> record = awaitEvent(
                "TASK_CREATED",
                taskId
        );

        assertEquals(
                String.valueOf(taskId),
                record.key()
        );
        assertEquals(
                "TASK_CREATED",
                record.value().getEventType()
        );
        assertEquals(
                taskId,
                record.value().getTaskId()
        );
        assertEquals(
                "Kafka integration test",
                record.value().getTitle()
        );
        assertNull(
                record.value().getAssigneeId()
        );
    }

    @Test
    void assignUser_shouldUpdateDatabaseAndPublishKafkaEvent()
            throws Exception {

        User user = userRepository.save(
                new User(
                        null,
                        "Igor",
                        "igor.integration@example.com"
                )
        );

        Task task = taskRepository.save(
                new Task(
                        null,
                        "Assign integration task",
                        null,
                        "Description",
                        TaskStatus.NEW
                )
        );

        mockMvc.perform(
                        patch(
                                "/tasks/{taskId}/assignee/{userId}",
                                task.getId(),
                                user.getId()
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.assigneeId")
                                .value(user.getId())
                )
                .andExpect(
                        jsonPath("$.assigneeName")
                                .value("Igor")
                );

        Task updatedTask = taskRepository.findById(task.getId())
                .orElseThrow();

        assertNotNull(updatedTask.getAssignee());
        assertEquals(
                user.getId(),
                updatedTask.getAssignee().getId()
        );

        ConsumerRecord<String, TaskEvent> record = awaitEvent(
                "TASK_ASSIGNED",
                task.getId()
        );

        assertEquals(
                String.valueOf(task.getId()),
                record.key()
        );
        assertEquals(
                "TASK_ASSIGNED",
                record.value().getEventType()
        );
        assertEquals(
                task.getId(),
                record.value().getTaskId()
        );
        assertEquals(
                user.getId(),
                record.value().getAssigneeId()
        );
        assertEquals(
                "Assign integration task",
                record.value().getTitle()
        );
    }

    @Test
    void updateStatus_shouldPersistNewStatus() throws Exception {
        Task task = taskRepository.save(
                new Task(
                        null,
                        "Update integration status",
                        null,
                        "Description",
                        TaskStatus.NEW
                )
        );

        mockMvc.perform(
                        patch(
                                "/tasks/{taskId}/status",
                                task.getId()
                        )
                                .contentType("application/json")
                                .content("""
                                        {
                                          "status": "IN_PROGRESS"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("IN_PROGRESS")
                );

        Task updatedTask = taskRepository.findById(task.getId())
                .orElseThrow();

        assertEquals(
                TaskStatus.IN_PROGRESS,
                updatedTask.getStatus()
        );
    }

    private Consumer<String, TaskEvent> createConsumer() {
        Map<String, Object> properties = new HashMap<>();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers()
        );

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "task-flow-integration-" + UUID.randomUUID()
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "latest"
        );

        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        JsonDeserializer<TaskEvent> valueDeserializer =
                new JsonDeserializer<>(TaskEvent.class);

        valueDeserializer.addTrustedPackages(
                "org.example.kafka"
        );

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                valueDeserializer
        ).createConsumer();
    }

    private void assignConsumerFromEnd(
            Consumer<String, TaskEvent> kafkaConsumer
    ) {
        /*
         * Получаем реальные partition топика.
         * Метод одновременно дожидается появления топика в Kafka.
         */
        List<TopicPartition> partitions = kafkaConsumer
                .partitionsFor(
                        TaskEventProducer.TOPIC,
                        Duration.ofSeconds(10)
                )
                .stream()
                .map(partitionInfo -> new TopicPartition(
                        partitionInfo.topic(),
                        partitionInfo.partition()
                ))
                .toList();

        if (partitions.isEmpty()) {
            throw new IllegalStateException(
                    "Topic has no partitions: "
                            + TaskEventProducer.TOPIC
            );
        }

        /*
         * assign() используется вместо subscribe().
         * Consumer не вступает в группу и не проходит ребалансировку.
         */
        kafkaConsumer.assign(partitions);

        /*
         * Пропускаем события, оставшиеся от предыдущих тестов.
         */
        kafkaConsumer.seekToEnd(partitions);

        /*
         * seekToEnd выполняется лениво.
         * position() заставляет consumer определить конечный offset сейчас.
         */
        for (TopicPartition partition : partitions) {
            kafkaConsumer.position(partition);
        }
    }

    private ConsumerRecord<String, TaskEvent> awaitEvent(
            String expectedEventType,
            Long expectedTaskId
    ) {
        /*
         * KafkaTemplate.send() асинхронный.
         * flush() дожидается отправки накопленных сообщений.
         */
        kafkaTemplate.flush();

        /*
         * В отличие от getSingleRecord(), этот метод ждёт,
         * пока будет получена хотя бы одна запись.
         */
        var records = KafkaTestUtils.getRecords(
                consumer,
                Duration.ofSeconds(20),
                1
        );

        for (ConsumerRecord<String, TaskEvent> record :
                records.records(TaskEventProducer.TOPIC)) {

            TaskEvent event = record.value();

            if (event == null) {
                continue;
            }

            boolean correctType = expectedEventType.equals(
                    event.getEventType()
            );

            boolean correctTask = expectedTaskId.equals(
                    event.getTaskId()
            );

            if (correctType && correctTask) {
                return record;
            }
        }

        throw new IllegalStateException(
                "Kafka event not found: eventType="
                        + expectedEventType
                        + ", taskId="
                        + expectedTaskId
                        + ", receivedRecords="
                        + records.count()
        );
    }
}