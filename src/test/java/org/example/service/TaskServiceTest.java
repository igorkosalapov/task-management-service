package org.example.service;

import org.example.dto.CreateTaskRequest;
import org.example.dto.TaskResponse;
import org.example.dto.UpdateTaskStatusRequest;
import org.example.entity.Task;
import org.example.entity.TaskStatus;
import org.example.entity.User;
import org.example.exception.TaskNotFoundException;
import org.example.exception.UserNotFoundException;
import org.example.kafka.TaskEventProducer;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskEventProducer taskEventProducer;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createTask_shouldSaveTaskWithNewStatusAndPublishEvent() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Prepare Kafka tests");
        request.setDescription("Cover task creation flow");

        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(10L);
            return task;
        });

        TaskResponse response = taskService.createTask(request);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(taskCaptor.capture());
        verify(taskEventProducer).sendTaskCreatedEvent(10L, "Prepare Kafka tests");

        Task savedTask = taskCaptor.getValue();
        assertEquals("Prepare Kafka tests", savedTask.getTitle());
        assertEquals("Cover task creation flow", savedTask.getDescription());
        assertEquals(TaskStatus.NEW, savedTask.getStatus());
        assertNull(savedTask.getAssignee());

        assertEquals(10L, response.getId());
        assertEquals(TaskStatus.NEW, response.getStatus());
        assertNull(response.getAssigneeId());
        assertNull(response.getAssigneeName());
    }

    @Test
    void getTaskById_shouldReturnTask() {
        User assignee = new User(7L, "Igor", "igor@example.com");
        Task task = new Task(
                1L,
                "Review pull request",
                assignee,
                "Check tests",
                TaskStatus.IN_PROGRESS
        );
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.getTaskById(1L);

        assertEquals(1L, response.getId());
        assertEquals("Review pull request", response.getTitle());
        assertEquals(TaskStatus.IN_PROGRESS, response.getStatus());
        assertEquals(7L, response.getAssigneeId());
        assertEquals("Igor", response.getAssigneeName());
    }

    @Test
    void getTaskById_whenTaskDoesNotExist_shouldThrowTaskNotFoundException() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        TaskNotFoundException exception = assertThrows(
                TaskNotFoundException.class,
                () -> taskService.getTaskById(999L)
        );

        assertEquals("Task not found with id: 999", exception.getMessage());
    }

    @Test
    void getTasks_shouldReturnMappedPage() {
        Pageable pageable = PageRequest.of(0, 2);
        Task firstTask = new Task(1L, "First", null, "Description 1", TaskStatus.NEW);
        Task secondTask = new Task(2L, "Second", null, "Description 2", TaskStatus.DONE);
        Page<Task> taskPage = new PageImpl<>(List.of(firstTask, secondTask), pageable, 5);
        when(taskRepository.findAll(pageable)).thenReturn(taskPage);

        Page<TaskResponse> responsePage = taskService.getTasks(pageable);

        assertEquals(2, responsePage.getContent().size());
        assertEquals("First", responsePage.getContent().get(0).getTitle());
        assertEquals("Second", responsePage.getContent().get(1).getTitle());
        assertEquals(5, responsePage.getTotalElements());
        assertEquals(3, responsePage.getTotalPages());
        verify(taskRepository).findAll(pageable);
    }

    @Test
    void assignUser_shouldAssignUserAndPublishEvent() {
        Task task = new Task(3L, "Implement Kafka producer", null, "Description", TaskStatus.NEW);
        User user = new User(4L, "Anna", "anna@example.com");
        when(taskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.assignUser(3L, 4L);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(taskCaptor.capture());
        verify(taskEventProducer).sendTaskAssignedEvent(3L, "Implement Kafka producer", 4L);

        assertEquals(user, taskCaptor.getValue().getAssignee());
        assertEquals(4L, response.getAssigneeId());
        assertEquals("Anna", response.getAssigneeName());
    }

    @Test
    void assignUser_whenTaskDoesNotExist_shouldThrowTaskNotFoundException() {
        when(taskRepository.findById(55L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> taskService.assignUser(55L, 4L));

        verifyNoInteractions(userRepository, taskEventProducer);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void assignUser_whenUserDoesNotExist_shouldThrowUserNotFoundException() {
        Task task = new Task(3L, "Implement Kafka producer", null, "Description", TaskStatus.NEW);
        when(taskRepository.findById(3L)).thenReturn(Optional.of(task));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> taskService.assignUser(3L, 404L)
        );

        assertEquals("User not found with id: 404", exception.getMessage());
        verify(taskRepository, never()).save(any(Task.class));
        verifyNoInteractions(taskEventProducer);
    }

    @Test
    void updateStatus_shouldChangeAndSaveTaskStatus() {
        Task task = new Task(8L, "Run tests", null, "Description", TaskStatus.NEW);
        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest();
        request.setStatus(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(8L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateStatus(8L, request);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertEquals(TaskStatus.IN_PROGRESS, taskCaptor.getValue().getStatus());
        assertEquals(TaskStatus.IN_PROGRESS, response.getStatus());
        verifyNoInteractions(taskEventProducer);
    }

    @Test
    void updateStatus_whenTaskDoesNotExist_shouldThrowTaskNotFoundException() {
        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest();
        request.setStatus(TaskStatus.DONE);
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> taskService.updateStatus(99L, request));

        verify(taskRepository, never()).save(any(Task.class));
        verifyNoInteractions(taskEventProducer);
    }
}
