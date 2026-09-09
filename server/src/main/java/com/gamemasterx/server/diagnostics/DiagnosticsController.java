package com.gamemasterx.server.diagnostics;

import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private final MongoClient mongoClient;

    public DiagnosticsController(MongoClient mongoClient) {
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

    @GetMapping("/mongodb")
    public Map<String, Object> checkMongoDbConnectivity() {
        Map<String, Object> details = new HashMap<>();
        boolean reachable = false;
        String mongoMessage = "MongoDB unreachable";
        boolean replicaSetMember = false;
        String replicaSetName = "";
        boolean transactionSupported = false;
        try {
            var commandResult = mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
            reachable = commandResult != null;
            mongoMessage = reachable ? "MongoDB connection is healthy" : "MongoDB ping returned no result";
            try {
                Document helloResult = mongoClient.getDatabase("admin").runCommand(new Document("hello", 1));
                replicaSetMember = helloResult.getBoolean("isReplicaSet", false);
                replicaSetName = helloResult.getString("setName");
                if (replicaSetName == null) replicaSetName = "";
                Integer logicalSessionTimeoutMinutes = helloResult.getInteger("logicalSessionTimeoutMinutes", 0);
                transactionSupported = replicaSetMember && logicalSessionTimeoutMinutes > 0;
            } catch (Exception e) {
                // defaults remain
            }
        } catch (Exception e) {
            mongoMessage = "MongoDB connection failed: " + e.getMessage();
        }
        details.put("reachable", reachable);
        details.put("replicaSetMember", replicaSetMember);
        details.put("replicaSetName", replicaSetName);
        details.put("transactionSupported", transactionSupported);
        String status = reachable ? "UP" : "DOWN";
        String message = reachable ? "MongoDB is reachable" : "MongoDB is unreachable";
        return buildEnvelope(status, message, details);
    }

    @GetMapping("/application")
    public Map<String, Object> applicationDiagnostics() {
        Map<String, Object> details = new HashMap<>();
        boolean replicaSetMember = false;
        String replicaSetName = "";
        boolean transactionSupported = false;
        boolean reachable = false;
        try {
            var commandResult = mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
            reachable = commandResult != null;
            Document helloResult = mongoClient.getDatabase("admin").runCommand(new Document("hello", 1));
            replicaSetMember = helloResult.getBoolean("isReplicaSet", false);
            replicaSetName = helloResult.getString("setName");
            if (replicaSetName == null) replicaSetName = "";
            Integer logicalSessionTimeoutMinutes = helloResult.getInteger("logicalSessionTimeoutMinutes", 0);
            transactionSupported = replicaSetMember && logicalSessionTimeoutMinutes > 0;
        } catch (Exception e) {
            // keep defaults
        }
        details.put("mongodbReachable", reachable);
        details.put("replicaSetMember", replicaSetMember);
        details.put("replicaSetName", replicaSetName);
        details.put("transactionSupported", transactionSupported);
        String status = reachable ? "UP" : "DOWN";
        String message = reachable ? "Application diagnostics available" : "Application diagnostics incomplete - MongoDB unreachable";
        return buildEnvelope(status, message, details);
    }
}
