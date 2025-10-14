package srf.inte.edu.berufsmesse.ai.selfies.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import srf.inte.edu.berufsmesse.ai.selfies.model.WorkflowStatusRequest;
import srf.inte.edu.berufsmesse.ai.selfies.service.WorkflowStatusService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflow-status")
@RequiredArgsConstructor
public class WorkflowStatusApi {

    private final WorkflowStatusService workflowStatusService;

    @GetMapping
    public ResponseEntity<Map<String, String>> getWorkflowStatus(@RequestBody
                                                                 WorkflowStatusRequest workflowStatusRequest) {
        Map<String, String> status = workflowStatusService.getStatus(workflowStatusRequest.getWorkflowId());
        return ResponseEntity.ok(status);
    }
}
