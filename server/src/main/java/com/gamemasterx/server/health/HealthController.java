package com.gamemasterx.server.health;

import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final MongoClient mongoClient;

    public HealthController(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    private Map<String, Object> buildEnvelope(String status, String message, Map<String, Object> details) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("status", status);
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("message", message);
        envelope.put("details", details != null ? details : new HashMap<>());
        return envelope;
    }

    @GetMapping("/liveness")
    public Map<String, Object> liveness() {
        Map<String, Object> details = new HashMap<>();
        return buildEnvelope("UP", "Application is live", details);
    }

    @GetMapping("/readiness")
    public Map<String, Object> readiness() {
        Map<String, Object> details = new HashMap<>();
        boolean reachable = false;
        String mongoMessage = "MongoDB unreachable";
        try {
            var commandResult = mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
            reachable = commandResult != null;
            mongoMessage = reachable ? "MongoDB connection is healthy" : "MongoDB ping returned no result";
        } catch (Exception e) {
            mongoMessage = "MongoDB connection failed: " + e.getMessage();
        }
        details.put("mongodb", Map.of(
                "status", reachable ? "UP" : "DOWN",
                "reachable", reachable,
                "message", mongoMessage
        ));
        String overallStatus = reachable ? "UP" : "DOWN";
        String message = reachable ? "Application is ready" : "Application is not ready - MongoDB unreachable";
        return buildEnvelope(overallStatus, message, details);
    }
}
