package org.example.service;

import lombok.RequiredArgsConstructor;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskEventProducer taskEventProducer;

    public TaskResponse createTask(CreateTaskRequest request) {
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(TaskStatus.NEW);

        Task savedTask = taskRepository.save(task);

        taskEventProducer.sendTaskCreatedEvent(
                savedTask.getId(),
                savedTask.getTitle()
        );

        return toResponse(savedTask);
    }

    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));

        return toResponse(task);
    }

    public Page<TaskResponse> getTasks(Pageable pageable) {
        return taskRepository.findAll(pageable)
                .map(this::toResponse);
    }

    public TaskResponse assignUser(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        task.setAssignee(user);

        Task savedTask = taskRepository.save(task);

        taskEventProducer.sendTaskAssignedEvent(
                savedTask.getId(),
                savedTask.getTitle(),
                user.getId()
        );

        return toResponse(savedTask);
    }

    public TaskResponse updateStatus(Long taskId, UpdateTaskStatusRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

        task.setStatus(request.getStatus());

        Task savedTask = taskRepository.save(task);

        return toResponse(savedTask);
    }

    private TaskResponse toResponse(Task task) {
        Long assigneeId = null;
        String assigneeName = null;

        if (task.getAssignee() != null) {
            assigneeId = task.getAssignee().getId();
            assigneeName = task.getAssignee().getName();
        }
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                assigneeId,
                assigneeName
        );
    }
}
