package srf.inte.edu.berufsmesse.ai.selfies.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class QueueResponse {
    @JsonProperty("queue_running")
    private List<List<Object>> queueRunning;

    @JsonProperty("queue_pending")
    private List<List<Object>> queuePending;
}
