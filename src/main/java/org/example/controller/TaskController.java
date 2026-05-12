package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.CreateTaskRequest;
import org.example.dto.TaskResponse;
import org.example.dto.UpdateTaskStatusRequest;
import org.example.service.TaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public TaskResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    @GetMapping("/{id}")
    public TaskResponse getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id);

    }

    @GetMapping
    public Page<TaskResponse> getTasks(Pageable pageable) {
        return taskService.getTasks(pageable);
    }

    @PatchMapping("/{taskId}/assignee/{userId}")
    public TaskResponse assignUser(@PathVariable Long taskId, @PathVariable Long userId) {
        return taskService.assignUser(taskId, userId);
    }

    @PatchMapping("/{taskId}/status")
    public TaskResponse updateStatus(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskStatusRequest request) {
        return taskService.updateStatus(taskId, request);
    }
}