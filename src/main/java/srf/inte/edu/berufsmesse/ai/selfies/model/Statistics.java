package srf.inte.edu.berufsmesse.ai.selfies.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Statistics {
    private int totalRequests = 0;
    private Map<String, Integer> gender = new HashMap<>();
    private Map<String, Integer> templates = new HashMap<>();
}

