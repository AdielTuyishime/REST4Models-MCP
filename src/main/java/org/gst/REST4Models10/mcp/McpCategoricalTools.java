package org.gst.REST4Models10.mcp;

import java.util.Map;

import org.gst.REST4Models10.service.M2MTransformationService;
import org.gst.REST4Models10.service.M2TTransformationService;
import org.gst.REST4Models10.service.Services;
import org.gst.REST4Models10.service.SkillService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Categorical MCP tools for model management, classified according to
 * MDE literature (Brambilla 2017, Lucio 2016, Lammel 2018).
 *
 * These tools replace the hardcoded transformation tools (m2m_transformation,
 * m2t_transformation, transform_xmi_to_json) with
 * parameterized categorical tools that dispatch to specific endpoints.
 *
 * The AI agent calls discover() first to read skill descriptors, then
 * selects the appropriate tool and endpoint based on the user's intent
 * and the available artifacts in the repository.
 */
@Component
public class McpCategoricalTools {

    /**
     * When true (the default), {@code discover} returns a compact index and the full
     * descriptor is fetched per capability. When false, it returns the full descriptors
     * inline. The two settings are the only difference between the two experimental
     * conditions, so they run on the same binary.
     */
    @Value("${mde.discover.compact:true}")
    private boolean compactDiscover;

    private final SkillService skillService;
    private final Services services;
    private final M2TTransformationService m2tService;
    private final M2MTransformationService m2mService;

    public McpCategoricalTools(
            SkillService skillService,
            Services services,
            M2TTransformationService m2tService,
            M2MTransformationService m2mService) {
        this.skillService = skillService;
        this.services = services;
        this.m2tService = m2tService;
        this.m2mService = m2mService;
    }

    // -------------------------------------------------------------------------
    // DISCOVER
    // -------------------------------------------------------------------------

    @McpTool(
        name = "discover",
        // The wording here is load-bearing, not cosmetic: rewriting this description
        // more than doubled the rate at which a local model stops emitting native tool
        // calls (10% -> 22%, measured). Only the endpoint-schema clause is changed from
        // the original, because it is false once discover returns a compact index.
        description = "List available model management tools and their capabilities. "
            + "Returns structured skill descriptors with MDE classification "
            + "(Brambilla form, Lucio transformation intents, "
            + "Lammel metaprogramming scenarios). "
            + "Categorical descriptors (m2m, m2t, t2m) include the capability_id to name "
            + "in the tool call, the form and format of the output, and chainable_with "
            + "information for pipeline construction; call discover with capability_id "
            + "to get that capability's full input schema. "
            + "Catalog descriptors (editing, navigation, utilities) list atomic "
            + "tools with CRUD type, entity, and pre/postconditions. "
            + "Call with category='all' or omit to get the complete platform map. "
            + "Filter by: 'm2m', 'm2t', 't2m', 'editing', 'navigation', 'utilities'."
    )
    public String discover(
        @McpToolParam(
            description = "Filter by category: "
                + "'m2m' (model-to-model transformations), "
                + "'m2t' (model-to-text transformations), "
                + "'t2m' (text-to-model transformations), "
                + "'editing' or 'metamodel_editing' (M2 metamodel CRUD), "
                + "'model_editing' (M1 instance CRUD), "
                + "'navigation' or 'model_navigation' (read-only queries), "
                + "'utilities' or 'repository_utilities' (file browsing), "
                + "or 'all' (default). "
                + "Returns skill descriptor JSON.",
            required = false
        )
        String category,
        @McpToolParam(
            description = "Optional. The capability_id of a single capability: returns that "
                + "capability's FULL descriptor (including its input_schema) instead of the "
                + "index. Omit to get the index.",
            required = false
        )
        String capability_id
    ) {
        if (capability_id != null && !capability_id.isBlank()) {
            var descriptor = skillService.getStandardDescriptorById(capability_id);
            if (descriptor == null) {
                return "{\"error\":\"Unknown capability_id: '" + capability_id
                    + "'. Call discover() with no capability_id to see the index.\"}";
            }
            return skillService.toJson(java.util.List.of(descriptor));
        }

        if (category == null || category.isBlank() || category.equals("all")) {
            return skillService.toJson(project(skillService.getStandardDescriptors()));
        }

        // Map shortcuts / legacy category names onto the 7 standard descriptor
        // categories, then return the matching standard capability descriptors.
        String descriptorCategory = switch (category.toLowerCase().trim()) {
            case "m2m", "model_to_model"                        -> "model_to_model";
            case "m2t", "model_to_text"                         -> "model_to_text";
            case "t2m", "text_to_model"                         -> "text_to_model";
            case "navigation", "model_navigation", "navigation_query" -> "navigation_query";
            case "editing", "metamodel_editing"                 -> "metamodel_editing";
            case "model_editing"                                -> "model_editing";
            case "utilities", "repository_utilities"            -> "repository_utilities";
            // Pass through for exact category names
            default -> category;
        };

        return skillService.toJson(
            project(skillService.getStandardDescriptorsByCategory(descriptorCategory)));
    }

    /** Compact index or full descriptors, per {@code mde.discover.compact}. */
    private java.util.List<com.fasterxml.jackson.databind.JsonNode> project(
            java.util.List<com.fasterxml.jackson.databind.JsonNode> descriptors) {
        return compactDiscover ? skillService.toCompactIndex(descriptors) : descriptors;
    }

    // -------------------------------------------------------------------------
    // MODEL-TO-MODEL (M2M)
    // -------------------------------------------------------------------------

    @McpTool(
        name = "model_to_model",
        description = "Model-to-Model transformation (Brambilla M2M). "
            + "Both input and output are models conforming to metamodels. "
            + "Call discover('m2m') to see available endpoints and their "
            + "chainable_with chains. Name the capability you chose in "
            + "'capability_id'; the server resolves the endpoint for you."
    )
    public Map<String, Object> modelToModel(
        @McpToolParam(
            description = "Source model filename in the repository (e.g., VehicleDefinitions.xmi)",
            required = true
        )
        String model,

        @McpToolParam(
            description = "Source metamodel name (e.g., SysML). "
                + "Used for context and endpoint selection.",
            required = true
        )
        String metamodel,

        @McpToolParam(
            description = "The capability_id of the capability you selected from discover(). "
                + "The server resolves the concrete endpoint from that capability's "
                + "descriptor: you never name an engine-level id. No capability id is "
                + "listed here on purpose -- call discover() to learn which ones exist.",
            required = true
        )
        String capability_id,

        @McpToolParam(
            description = "Output filename (optional, auto-generated if omitted)",
            required = false
        )
        String output
    ) throws Exception {

        String outFile = (output == null || output.isBlank())
            ? model.replaceFirst("\\.[^.]+$", "_transformed.xmi")
            : output;

        // RA decision: discovery and dispatch are SERVER-side. The client names a
        // capability_id it saw in discover(); the engine-level selector lives in that
        // capability's binding.static_args and is resolved HERE, exactly as the gateway's
        // router does it. `transformation` remains accepted as a deprecated alias.
        // ONE vocabulary, everywhere. The client names a capability_id it saw in discover();
        // the server resolves the engine-level endpoint from that capability's binding. The
        // gateway's router injects the same capability_id, so both realizations of the RA
        // speak the same language and the model never sees an engine-level identifier.
        String endpoint = skillService.resolveEndpointSelector(capability_id);
        if (endpoint == null || endpoint.isBlank()) {
            return Map.<String, Object>of(
                // Do NOT enumerate the catalogue here. A client with no discover only has
                // to guess wrong ONCE to be handed every capability id for free -- and the
                // M1 baseline did exactly that: guess, read the list off the error, then
                // call the right one. The error says where the names live, not what they are.
                "error", "Unknown or missing capability_id: '" + capability_id + "'. "
                    + "Call discover() to see which capabilities this server offers."
            );
        }

        return switch (endpoint) {
            case "sysml2json_etl" ->
                m2mService.transformSysmlToJsonModel(model, outFile);
            default ->
                Map.of("error", "Unknown M2M endpoint bound to capability_id '" + capability_id + "'"
                    + ". Call discover('m2m') to see available endpoints.");
        };
    }

    // -------------------------------------------------------------------------
    // MODEL-TO-TEXT (M2T)
    // -------------------------------------------------------------------------

    @McpTool(
        name = "model_to_text",
        description = "Model-to-Text transformation (Brambilla M2T). "
            + "Input is a model, output is text (JSON, code, config). "
            + "Covers code generation (Lucio synthesis), serialization, "
            + "and translation to text formats. "
            + "Call discover('m2t') for available endpoints."
    )
    public Object modelToText(
        @McpToolParam(
            description = "Source model filename in the repository "
                + "(e.g., VehicleDefinitions.xmi or SysML_model.json)",
            required = true
        )
        String model,

        @McpToolParam(
            description = "Source metamodel name (e.g., SysML, JSON). "
                + "Helps select the right endpoint.",
            required = true
        )
        String metamodel,

        @McpToolParam(
            description = "The capability_id of the capability you selected from discover(). "
                + "The server resolves the concrete endpoint from that capability's "
                + "descriptor: you never name an engine-level id. No capability id is "
                + "listed here on purpose -- call discover() to learn which ones exist.",
            required = true
        )
        String capability_id,

        @McpToolParam(
            description = "Output filename (optional, auto-generated if omitted)",
            required = false
        )
        String output
    ) throws Exception {

        String outFile = (output == null || output.isBlank())
            ? model.replaceFirst("\\.[^.]+$", "_output.json")
            : output;

        // RA decision: discovery and dispatch are SERVER-side. The client names a
        // capability_id it saw in discover(); the engine-level selector lives in that
        // capability's binding.static_args and is resolved HERE, exactly as the gateway's
        // router does it. `transformation` remains accepted as a deprecated alias.
        // ONE vocabulary, everywhere. The client names a capability_id it saw in discover();
        // the server resolves the engine-level endpoint from that capability's binding. The
        // gateway's router injects the same capability_id, so both realizations of the RA
        // speak the same language and the model never sees an engine-level identifier.
        String endpoint = skillService.resolveEndpointSelector(capability_id);
        if (endpoint == null || endpoint.isBlank()) {
            return Map.<String, Object>of(
                // Do NOT enumerate the catalogue here. A client with no discover only has
                // to guess wrong ONCE to be handed every capability id for free -- and the
                // M1 baseline did exactly that: guess, read the list off the error, then
                // call the right one. The error says where the names live, not what they are.
                "error", "Unknown or missing capability_id: '" + capability_id + "'. "
                    + "Call discover() to see which capabilities this server offers."
            );
        }

        return switch (endpoint) {

            // Epsilon EGL: SysML XMI → generic SysML JSON
            case "sysml2json_egl" ->
                m2tService.exportSysmlXmiToJsonAndSave(model, outFile);


            // Java Jackson: any XMI → JSON (technological space bridge)
            case "xmi_to_json_jackson" ->
                services.transformXmiToJsonAndSave(model);


            default ->
                Map.of("error", "Unknown M2T endpoint bound to capability_id '" + capability_id + "'"
                    + ". Call discover('m2t') to see available endpoints.");
        };
    }

    // -------------------------------------------------------------------------
    // TEXT-TO-MODEL (T2M)
    // -------------------------------------------------------------------------

    @McpTool(
        name = "text_to_model",
        description = "Text-to-Model transformation (Brambilla T2M). "
            + "Input is text (source code, DSL notation), output is a model "
            + "conforming to a metamodel. Covers injection and parsing "
            + "(Lucio reverse_engineering/parsing intents). "
            + "Call discover('t2m') for available endpoints."
    )
    public Object textToModel(
        @McpToolParam(
            description = "Source text filename in the repository (e.g., Vehicle.sysml)",
            required = true
        )
        String text,

        @McpToolParam(
            description = "The capability_id of the capability you selected from discover(). "
                + "The server resolves the concrete endpoint from that capability's "
                + "descriptor: you never name an engine-level id. No capability id is "
                + "listed here on purpose -- call discover() to learn which ones exist.",
            required = true
        )
        String capability_id,

        @McpToolParam(
            description = "Output model filename (optional, auto-generated if omitted)",
            required = false
        )
        String output
    ) throws Exception {

        // RA decision: discovery and dispatch are SERVER-side. The client names a
        // capability_id it saw in discover(); the engine-level selector lives in that
        // capability's binding.static_args and is resolved HERE, exactly as the gateway's
        // router does it. `transformation` remains accepted as a deprecated alias.
        // ONE vocabulary, everywhere. The client names a capability_id it saw in discover();
        // the server resolves the engine-level endpoint from that capability's binding. The
        // gateway's router injects the same capability_id, so both realizations of the RA
        // speak the same language and the model never sees an engine-level identifier.
        String endpoint = skillService.resolveEndpointSelector(capability_id);
        if (endpoint == null || endpoint.isBlank()) {
            return Map.<String, Object>of(
                // Do NOT enumerate the catalogue here. A client with no discover only has
                // to guess wrong ONCE to be handed every capability id for free -- and the
                // M1 baseline did exactly that: guess, read the list off the error, then
                // call the right one. The error says where the names live, not what they are.
                "error", "Unknown or missing capability_id: '" + capability_id + "'. "
                    + "Call discover() to see which capabilities this server offers."
            );
        }

        return switch (endpoint) {

            case "sysml_text_to_xmi" ->
                // TODO: integrate SysML v2 Pilot JAR when available from Adiel
                Map.of("error", "sysml_text_to_xmi endpoint not yet implemented. "
                    + "Pending SysML v2 Pilot JAR integration.");

            default ->
                Map.of("error", "Unknown T2M endpoint bound to capability_id '" + capability_id + "'"
                    + ". Call discover('t2m') to see available endpoints.");
        };
    }

    // -------------------------------------------------------------------------
    // NAVIGATION-QUERY
    // -------------------------------------------------------------------------

    @McpTool(
        name = "navigation_query",
        description = "Read-only query over the repository (Brambilla navigation). "
            + "Answers questions about what is in the repository without changing it. "
            + "Call discover('navigation') to see available endpoints and their "
            + "chainable_with chains. Name the capability you chose in "
            + "'capability_id'; put its own arguments in 'args'."
    )
    public Object navigationQuery(
        @McpToolParam(
            description = "The capability_id of the capability you selected from discover(). "
                + "No capability id is listed here on purpose -- call discover() to learn "
                + "which ones exist.",
            required = true
        )
        String capability_id,

        @McpToolParam(
            description = "The arguments that capability's input_schema declares "
                + "(e.g., {\"modelFileName\": \"VehicleDefinitions.xmi\"}). "
                + "Omit for capabilities that take none.",
            required = false
        )
        Map<String, Object> args
    ) {
        String endpoint = skillService.resolveEndpointSelector(capability_id);
        if (endpoint == null || endpoint.isBlank()) {
            return Map.<String, Object>of(
                // Do NOT enumerate the catalogue here. A client with no discover only has
                // to guess wrong ONCE to be handed every capability id for free -- and the
                // M1 baseline did exactly that: guess, read the list off the error, then
                // call the right one. The error says where the names live, not what they are.
                "error", "Unknown or missing capability_id: '" + capability_id + "'. "
                    + "Call discover() to see which capabilities this server offers."
            );
        }
        Map<String, Object> a = (args == null) ? Map.of() : args;

        return switch (endpoint) {
            case "list_packages" ->
                services.getAllRegisteredEPackages();

            case "list_packages_with_models" ->
                services.listPackagesWithXmiFiles();

            case "read_model_content" ->
                services.readXmiAsJson(str(a, "modelFileName"));

            default ->
                Map.of("error", "Unknown navigation endpoint bound to capability_id '"
                    + capability_id + "'. Call discover('navigation') to see available endpoints.");
        };
    }

    // -------------------------------------------------------------------------
    // REPOSITORY-UTILITIES
    // -------------------------------------------------------------------------

    @McpTool(
        name = "repository_utilities",
        description = "Browse and read repository artifacts (files, not models). "
            + "Call discover('utilities') to see available endpoints and their "
            + "chainable_with chains. Name the capability you chose in "
            + "'capability_id'; put its own arguments in 'args'."
    )
    public Object repositoryUtilities(
        @McpToolParam(
            description = "The capability_id of the capability you selected from discover(). "
                + "No capability id is listed here on purpose -- call discover() to learn "
                + "which ones exist.",
            required = true
        )
        String capability_id,

        @McpToolParam(
            description = "The arguments that capability's input_schema declares "
                + "(e.g., {\"fileName\": \"VehicleDefinitions_output.json\"}). "
                + "Omit for capabilities that take none.",
            required = false
        )
        Map<String, Object> args
    ) {
        String endpoint = skillService.resolveEndpointSelector(capability_id);
        if (endpoint == null || endpoint.isBlank()) {
            return Map.<String, Object>of(
                // Do NOT enumerate the catalogue here. A client with no discover only has
                // to guess wrong ONCE to be handed every capability id for free -- and the
                // M1 baseline did exactly that: guess, read the list off the error, then
                // call the right one. The error says where the names live, not what they are.
                "error", "Unknown or missing capability_id: '" + capability_id + "'. "
                    + "Call discover() to see which capabilities this server offers."
            );
        }
        Map<String, Object> a = (args == null) ? Map.of() : args;

        return switch (endpoint) {
            case "list_json_files" ->
                services.listJsonFiles(Boolean.TRUE.equals(a.get("recursive")));

            case "read_json_file" ->
                services.readJsonFileContent(str(a, "fileName"));

            default ->
                Map.of("error", "Unknown utilities endpoint bound to capability_id '"
                    + capability_id + "'. Call discover('utilities') to see available endpoints.");
        };
    }

    /** Read a string argument out of the dispatched capability's own args. */
    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return (v == null) ? null : v.toString();
    }
}
