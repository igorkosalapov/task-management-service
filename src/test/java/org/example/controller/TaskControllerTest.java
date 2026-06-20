package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.TaskResponse;
import org.example.entity.TaskStatus;
import org.example.exception.GlobalExceptionHandler;
import org.example.exception.TaskNotFoundException;
import org.example.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @Test
    void createTask_withValidRequest_shouldReturnTask() throws Exception {
        TaskResponse response = new TaskResponse(
                1L,
                "Write tests",
                "Cover controller",
                TaskStatus.NEW,
                null,
                null
        );
        when(taskService.createTask(any())).thenReturn(response);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Write tests",
                                  "description": "Cover controller"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Write tests"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.assigneeId").value(nullValue()));

        verify(taskService).createTask(any());
    }

    @Test
    void createTask_withBlankTitle_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "description": "Description"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.title").exists());

        verifyNoInteractions(taskService);
    }

    @Test
    void getTaskById_shouldReturnTask() throws Exception {
        TaskResponse response = new TaskResponse(
                5L,
                "Read task",
                "Description",
                TaskStatus.IN_PROGRESS,
                2L,
                "Anna"
        );
        when(taskService.getTaskById(5L)).thenReturn(response);

        mockMvc.perform(get("/tasks/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assigneeId").value(2))
                .andExpect(jsonPath("$.assigneeName").value("Anna"));
    }

    @Test
    void getTaskById_whenTaskDoesNotExist_shouldReturnNotFound() throws Exception {
        when(taskService.getTaskById(999L)).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get("/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Task not found with id: 999"))
                .andExpect(jsonPath("$.path").value("/tasks/999"));
    }

    @Test
    void getTasks_shouldReturnPage() throws Exception {
        List<TaskResponse> tasks = List.of(
                new TaskResponse(1L, "First", "One", TaskStatus.NEW, null, null),
                new TaskResponse(2L, "Second", "Two", TaskStatus.DONE, null, null)
        );
        PageImpl<TaskResponse> page = new PageImpl<>(tasks, PageRequest.of(0, 2), 5);
        when(taskService.getTasks(any())).thenReturn(page);

        mockMvc.perform(get("/tasks")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("First"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void assignUser_shouldReturnUpdatedTask() throws Exception {
        TaskResponse response = new TaskResponse(
                3L,
                "Assign task",
                "Description",
                TaskStatus.NEW,
                4L,
                "Bob"
        );
        when(taskService.assignUser(3L, 4L)).thenReturn(response);

        mockMvc.perform(patch("/tasks/3/assignee/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.assigneeId").value(4))
                .andExpect(jsonPath("$.assigneeName").value("Bob"));
    }

    @Test
    void updateStatus_withValidStatus_shouldReturnUpdatedTask() throws Exception {
        TaskResponse response = new TaskResponse(
                9L,
                "Update status",
                "Description",
                TaskStatus.DONE,
                null,
                null
        );
        when(taskService.updateStatus(eq(9L), any())).thenReturn(response);

        mockMvc.perform(patch("/tasks/9/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("status", "DONE")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void updateStatus_withInvalidStatus_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(patch("/tasks/9/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request body is invalid or contains an unsupported value"));

        verify(taskService, never()).updateStatus(eq(9L), any());
    }

    @Test
    void updateStatus_withoutStatus_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(patch("/tasks/9/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.status").exists());

        verify(taskService, never()).updateStatus(eq(9L), any());
    }
}
