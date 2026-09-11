package com.gamemasterx.server.campaign.websocket;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CampaignEventWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, Set<WebSocketSession>> sessionsByCampaign = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String campaignId = extractCampaignId(session);
        if (campaignId == null || campaignId.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        sessionsByCampaign.computeIfAbsent(campaignId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(session);
        // Send state refresh with current sequence number
        int seq = getCurrentSequence(campaignId);
        String statePayload = String.format("{\"type\":\"state\",\"campaignId\":\"%s\",\"sequenceNumber\":%d,\"timestamp\":\"%s\"}",
                campaignId, seq, Instant.now().toString());
        session.sendMessage(new TextMessage(statePayload));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String campaignId = extractCampaignId(session);
        if (campaignId != null) {
            Set<WebSocketSession> set = sessionsByCampaign.get(campaignId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    sessionsByCampaign.remove(campaignId);
                    sequenceCounters.remove(campaignId);
                }
            }
        }
    }

    private String extractCampaignId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : null;
        // Expected path: /ws/campaign/{campaignId}
        if (path != null && path.startsWith("/ws/campaign/")) {
            String id = path.substring("/ws/campaign/".length());
            // Strip query string
            int q = id.indexOf('?');
            if (q >= 0) {
                id = id.substring(0, q);
            }
            return id;
        }
        return null;
    }

    public static void broadcast(String campaignId, String eventType, String payload) {
        Set<WebSocketSession> sessions = sessionsByCampaign.get(campaignId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        int seq = nextSequence(campaignId);
        String message = String.format("{\"type\":\"%s\",\"campaignId\":\"%s\",\"sequenceNumber\":%d,\"timestamp\":\"%s\",\"payload\":%s}",
                eventType, campaignId, seq, Instant.now().toString(), payload);
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(message));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static int nextSequence(String campaignId) {
        AtomicInteger counter = sequenceCounters.computeIfAbsent(campaignId, k -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }

    private static int getCurrentSequence(String campaignId) {
        AtomicInteger counter = sequenceCounters.get(campaignId);
        return counter != null ? counter.get() : 0;
    }
}
