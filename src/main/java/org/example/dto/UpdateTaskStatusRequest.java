package org.example.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.TaskStatus;

@Getter
@Setter
public class UpdateTaskStatusRequest {

    @NotNull
    private TaskStatus status;
}
