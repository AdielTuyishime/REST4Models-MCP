package org.gst.REST4Models10.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;

/**
 * Loads the capability descriptors from the classpath at startup.
 *
 * Each descriptor is one ATOMIC selectable capability, conforming to
 * capability-descriptor.schema.json (v1.0.0), and carries its category, input
 * schema, output form and format, technical space, chainable_with edges, and the
 * transport-only binding through which the server routes it. This is what
 * discover() serves.
 *
 * @see resources/descriptors/*.json
 */
@Service
public class SkillService {

    private final ObjectMapper mapper = new ObjectMapper();

    /** The capability descriptors served by discover(), ordered by capability_id. */
    private final List<JsonNode> standardDescriptors = new ArrayList<>();

    @PostConstruct
    public void loadDescriptors() {
        loadStandardDescriptors();
    }

    /**
     * Load the STANDARD capability descriptors from resources/descriptors/*.json.
     * Uses a classpath pattern resolver so it works in both exploded-JAR and IDE
     * runs. Descriptors are sorted by capability_id for a deterministic discover order.
     */
    private void loadStandardDescriptors() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:descriptors/*.json");
            for (Resource res : resources) {
                try (InputStream is = res.getInputStream()) {
                    standardDescriptors.add(mapper.readTree(is));
                }
            }
            standardDescriptors.sort(Comparator.comparing(n -> n.path("capability_id").asText("")));
        } catch (IOException e) {
            System.err.println("[SkillService] Could not load standard descriptors: " + e.getMessage());
        }
        System.err.println("[SkillService] Loaded " + standardDescriptors.size() + " standard capability descriptor(s)");
    }

    /**
     * All STANDARD capability descriptors (the gateway self-description contract).
     * @return the descriptors as a list of JsonNode, ordered by capability_id
     */
    public List<JsonNode> getStandardDescriptors() {
        return standardDescriptors;
    }

    /**
     * STANDARD capability descriptors filtered by their {@code category} field.
     * @param category one of the 7 descriptor categories (e.g. "model_to_text")
     * @return descriptors whose category equals {@code category}
     */
    public List<JsonNode> getStandardDescriptorsByCategory(String category) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode d : standardDescriptors) {
            if (category.equals(d.path("category").asText())) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * A single STANDARD capability descriptor, looked up by its {@code capability_id}.
     * @param capabilityId the descriptor's own identifier
     * @return the descriptor, or null if no descriptor carries that id
     */
    public JsonNode getStandardDescriptorById(String capabilityId) {
        if (capabilityId == null || capabilityId.isBlank()) {
            return null;
        }
        for (JsonNode d : standardDescriptors) {
            if (capabilityId.equals(d.path("capability_id").asText())) {
                return d;
            }
        }
        return null;
    }

    /**
     * Compact index over a set of descriptors: enough to SELECT a capability and to
     * see what it can be chained with, but without the per-capability input_schema.
     * The argument shape is already advertised on the categorical dispatcher itself;
     * the full descriptor remains available on demand.
     * @param descriptors the descriptors to summarize
     * @return one entry per descriptor, in the same order
     */
    public List<JsonNode> toCompactIndex(List<JsonNode> descriptors) {
        List<JsonNode> index = new ArrayList<>();
        for (JsonNode d : descriptors) {
            ObjectNode e = mapper.createObjectNode();
            e.put("capability_id", d.path("capability_id").asText());
            e.put("category", d.path("category").asText());
            e.put("description", d.path("description").asText());
            e.put("output_form", d.path("output").path("form").asText());
            // The format is what distinguishes two capabilities of the same form:
            // two JSON renderings both have form "json". Dropping it makes them
            // indistinguishable in the index, and the client picks the wrong one.
            String format = d.path("output").path("format").asText("");
            if (!format.isBlank()) {
                e.put("output_format", format);
            }
            JsonNode chainable = d.get("chainable_with");
            if (chainable != null && chainable.isArray()) {
                e.set("chainable_with", chainable.deepCopy());
            }
            index.add(e);
        }
        return index;
    }

    /**
     * Resolve a client-facing capability_id to the engine-level endpoint selector.
     *
     * <p>The RA says discovery and dispatch are SERVER-side: the client reasons only over
     * the metadata it was returned, and names a capability. The engine-level selector
     * (the argument the Epsilon layer switches on) is deliberately
     * NOT in a descriptor's {@code input_schema}: it lives in {@code binding.static_args},
     * so the client never has to know it. In the categorical gateway a router injects it.
     * This method is the single-server's equivalent of that router -- without it, an agent
     * that follows the descriptors faithfully cannot invoke anything, which is exactly the
     * regression this fixes.
     *
     * @param capabilityId the capability_id the client named (e.g. "sysml2json_egl")
     * @return the endpoint selector from that capability's binding, or null if unknown
     */
    public String resolveEndpointSelector(String capabilityId) {
        if (capabilityId == null || capabilityId.isBlank()) {
            return null;
        }
        for (JsonNode descriptor : standardDescriptors) {
            JsonNode id = descriptor.get("capability_id");
            if (id != null && capabilityId.equals(id.asText())) {
                JsonNode binding = descriptor.get("binding");
                if (binding == null) {
                    return null;
                }
                JsonNode staticArgs = binding.get("static_args");
                if (staticArgs == null) {
                    return null;
                }
                JsonNode selector = staticArgs.get("capability_id");
                return selector == null ? null : selector.asText();
            }
        }
        return null;
    }

    /**
     * @return every capability_id this server advertises, for error messages that help
     *         rather than merely fail.
     */
    public List<String> knownCapabilityIds() {
        List<String> ids = new ArrayList<>();
        for (JsonNode descriptor : standardDescriptors) {
            JsonNode id = descriptor.get("capability_id");
            if (id != null) {
                ids.add(id.asText());
            }
        }
        return ids;
    }

    /**
     * Serialize descriptors to a JSON string for the MCP tool response.
     */
    public String toJson(Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (IOException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
