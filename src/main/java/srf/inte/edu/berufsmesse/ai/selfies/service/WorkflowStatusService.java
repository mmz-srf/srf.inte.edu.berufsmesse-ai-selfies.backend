package srf.inte.edu.berufsmesse.ai.selfies.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import srf.inte.edu.berufsmesse.ai.selfies.configuration.DocumentProperties;
import srf.inte.edu.berufsmesse.ai.selfies.model.QueueResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowStatusService {

    private final DocumentProperties documentProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, String> getStatus(String workflowId) {
        String comfyBaseUrl = documentProperties.getComfyuiUrl();
        String statusUrl = comfyBaseUrl + "/history/" + workflowId;
        String queueUrl = comfyBaseUrl + "/queue";

        Map<String, String> result = new HashMap<>();
        result.put("workflowId", workflowId);

        try {
            // Check if the workflow is finished
            ResponseEntity<Map> response = restTemplate.getForEntity(statusUrl, Map.class);
            Map<String, Object> body = response.getBody();

            // If the body is empty, the workflow is still pending
            if (body == null || body.isEmpty()) {
                ResponseEntity<QueueResponse> queueResponse = restTemplate.getForEntity(queueUrl, QueueResponse.class);
                QueueResponse queueBody = queueResponse.getBody();

                if (queueBody == null) {
                    result.put("status", "error");
                    result.put("message", "Queue response is null");
                    return result;
                }

                // List of running and pending workflows
                List<List<Object>> running = queueBody.getQueueRunning();
                List<List<Object>> pending = queueBody.getQueuePending();

                if (isWorkflowInQueue(pending, workflowId)) {
                    result.put("status", "pending");

                    // Get position and total of the workflow in the queue
                    int position = getQueuePosition(pending, workflowId);
                    int total = pending.size();

                    result.put("position", String.valueOf(position));
                    result.put("total", String.valueOf(total));
                    return result;
                } else if (isWorkflowInQueue(running, workflowId)) {
                    result.put("status", "running");
                    return result;
                }

                result.put("status", "unknown");
                result.put("message", "Workflow is not in the queue or finished");
                return result;
            }

            // Get image path from the body
            if (body.containsKey(workflowId)) {
                Map<String, Object> workflowData = (Map<String, Object>) body.get(workflowId);
                Map<String, Object> outputs = (Map<String, Object>) workflowData.get("outputs");
                if (outputs != null && outputs.containsKey("9")) {
                    Map<String, Object> node = (Map<String, Object>) outputs.get("9");
                    List<Map<String, Object>> images = (List<Map<String, Object>>) node.get("images");

                    if (images != null && !images.isEmpty()) {
                        String filename = images.getFirst().get("filename").toString();
                        result.put("status", "done");
                        result.put("filename", filename);
                        return result;
                    }
                }
            }

            result.put("status", "unknown");
            result.put("message", "Workflow is not part if the response body");
            return result;

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return error;
        }

    }

    // Check if a workflow is in the queue
    private boolean isWorkflowInQueue(List<List<Object>> queueList, String workflowId) {
        if (queueList == null) return false;

        for (List<Object> entry : queueList) {
            if (entry.size() >= 2 && workflowId.equals(entry.get(1).toString())) {
                return true;
            }
        }
        return false;
    }

    // Get the position of a workflow in the queue
    private int getQueuePosition(List<List<Object>> queueList, String workflowId) {
        if (queueList == null) return -1;

        for (int i = 0; i < queueList.size(); i++) {
            List<Object> entry = queueList.get(i);
            if (entry.size() >= 2 && workflowId.equals(entry.get(1).toString())) {
                return i + 1;
            }
        }
        return -1;
    }

}
