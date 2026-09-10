package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.Encounter.Resource;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#RESOURCES} dimension.
 *
 * <p>A {@link OperationType#CHANGE_RESOURCE} proposal names a resource on the
 * target participant and a numeric delta. The resource must exist and the delta
 * must keep the resource within its legal {@code [0, max]} bounds. Out-of-range
 * resource changes are rejected before any execution.</p>
 */

@Component
public class ResourceChecker implements OperationChecker {

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.RESOURCES;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        if (proposal.operationType() != OperationType.CHANGE_RESOURCE) {
            return Optional.empty();
        }
        Encounter encounter = context.getEncounter();
        if (encounter == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RESOURCES, "targetId",
                    "the target encounter must be loaded to verify resource bounds"));
        }
        Encounter.Participant participant = findParticipant(encounter, proposal.targetId());
        if (participant == null) {
            return Optional.empty();
        }
        String resourceName = stringParameter(proposal, "resourceName");
        if (resourceName == null || resourceName.isBlank()) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RESOURCES, "resourceName",
                    "a resource change must name the resource it changes"));
        }
        Integer delta = integerParameter(proposal, "delta");
        if (delta == null) {
            delta = integerParameter(proposal, "amount");
        }
        if (delta == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RESOURCES, "delta",
                    "a resource change must carry an integer 'delta' (or 'amount')"));
        }
        Resource resource = findResource(participant, resourceName);
        if (resource == null) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RESOURCES, "resourceName",
                    "participant " + proposal.targetId() + " has no resource named '"
                            + resourceName + "'"));
        }
        int resulting = resource.getCurrent() + delta;
        if (resulting < 0 || resulting > resource.getMax()) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.RESOURCES, "delta",
                    "changing '" + resourceName + "' by " + delta
                            + " would take it from " + resource.getCurrent()
                            + " to " + resulting + ", outside the legal [0, " + resource.getMax() + "] range"));
        }
        return Optional.empty();
    }

    private static Resource findResource(Encounter.Participant participant, String resourceName) {
        for (Resource r : participant.getResources()) {
            if (r.getName() != null && r.getName().equalsIgnoreCase(resourceName)) {
                return r;
            }
        }
        return null;
    }

    private static Encounter.Participant findParticipant(Encounter encounter, String participantId) {
        for (Encounter.Participant p : encounter.getParticipants()) {
            if (participantId != null && participantId.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }

    private static String stringParameter(ProposedOperation proposal, String key) {
        Object value = proposal.parameters().get(key);
        return (value instanceof String s) ? s : null;
    }

    private static Integer integerParameter(ProposedOperation proposal, String key) {
        Object value = proposal.parameters().get(key);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
