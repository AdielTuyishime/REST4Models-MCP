package org.gst.REST4Models10.mcp;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.gst.REST4Models10.repository.RepositoryScanner;
import org.gst.REST4Models10.service.M2MTransformationService;
import org.gst.REST4Models10.service.M2TTransformationService;
import org.gst.REST4Models10.service.ModelAnalysis;
import org.gst.REST4Models10.service.Services;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class McpTools {

	private final Services services;
	private final M2TTransformationService exportService;
	private final M2MTransformationService m2mtransformation;
	private final ModelAnalysis complexityService;
	

	public McpTools(Services services, M2TTransformationService exportService, M2MTransformationService m2mtransformation, ModelAnalysis complexityService) {
		this.services = services;
		this.exportService = exportService;
		this.m2mtransformation = m2mtransformation;
		this.complexityService = complexityService;
		
	}
	

	/**------------------------------------------------------------------------------
	 * MODEL OPERATIONS
	 * ------------------------------------------------------------------------------
	 */

	/**
	 * READ MODEL CONTENT
	 */
	@McpTool(name = "read_model_content",
			description = "Read XMI model content as hierarchical JSON (containment expanded, non-containment as _ref).")
	public List<Map<String, Object>> readModelContent(
			@McpToolParam(description = "Model file name, e.g., Family.xmi", required = true)
			String modelFileName) {

		return services.readXmiAsJson(modelFileName);
	}


	/**
	 * CREATE INSTANCE OF A CLASS
	 */
	@McpTool(name = "create_instance",
			description = "Create an instance of a class in a specific model file (attributes in JSON).")
	public String createInstance(
			@McpToolParam(description = "Model file name", required = true) String modelFileName,
			@McpToolParam(description = "Package name", required = true) String packageName,
			@McpToolParam(description = "Class name", required = true) String className,
			@McpToolParam(description = "Attribute map (string values)", required = true)
			Map<String, String> attributes) {

		boolean ok = services.insertNewInstance(packageName, className, attributes, modelFileName);
		if (!ok) throw new IllegalArgumentException("Insert failed.");
		return "New instance inserted and saved.";
	}


	/**
	 * GET instances in a specific model
	 */
	@McpTool(
			name = "get_instances_in_model",
			description = "Get instances of a given EClass from a specific XMI model file. "
					+ "Returns instances as JSON-like maps."
			)
	public List<Map<String, Object>> getInstancesInModel(
			@McpToolParam(description = "XMI model file name, e.g., Family.xmi", required = true)
			String modelFileName,

			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName,

			@McpToolParam(description = "EClass name, e.g., Person", required = true)
			String className
			) {
		List<EObject> instances =
				services.getInstancesOfClassFromSpecificFile(packageName, className, modelFileName);

		return instances.stream()
				.map(Services::eObjectToMapClass)
				.toList();
	}


	/**
	 * GET all instances across model(s) conforming to the same metamodel 
	 */
	@McpTool(
			name = "get_all_instances",
			description = "Get all instances of a given EClass across all model(s) that conform to the same metamodel. "
					+ "Returns instances as JSON-like maps."
			)
	public List<Map<String, Object>> getAllInstances(
			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName,

			@McpToolParam(description = "EClass name, e.g., Person", required = true)
			String className
			) {
		List<EObject> instances = services.getInstancesOfClass(packageName, className);

		// Map to JSON-like structure
		List<Map<String, Object>> mapped = instances.stream()
				.map(Services::eObjectToMap)
				.toList();

		// IMPORTANT: Avoid System.out in STDIO MCP servers (can break the protocol).
		// If you want diagnostics, use a logger (logs go to stderr depending on config).
		return mapped;
	}

	/**
	 * UPDATE INSTANCE (e.g: attribute)
	 */
	@McpTool(
			name = "update_instances_by_attribute",
			description =
			"Update instance(s) of a class in a specific model file by matching an attribute value. "
					+ "Returns a human-readable status message."
			)
	public String updateByAttribute(
			@McpToolParam(description = "XMI model file name, e.g., Family.xmi", required = true)
			String modelFileName,

			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName,

			@McpToolParam(description = "EClass name, e.g., Person", required = true)
			String className,

			@McpToolParam(description = "Attribute name to match, e.g., firstName", required = true)
			String attribute,

			@McpToolParam(description = "Old value to match", required = true)
			String oldValue,

			@McpToolParam(description = "New value to set", required = true)
			String newValue
			) {
		boolean updated = services.updateInstancesByAttribute(
				packageName, className, attribute, oldValue, newValue, modelFileName
				);

		// For MCP it's often better to throw on "not found" so the agent clearly sees failure.
		if (!updated) {
			throw new IllegalStateException("No matching instances found to update.");
		}

		return "✅ Updated matching instance(s).";
	}

	/**
	 * DELETE AN INSTANCE BY ATTRIBUTE
	 */
	@McpTool(
			name = "delete_instances_by_attribute",
			description =
			"Delete instance(s) of a class in a specific model file by matching an attribute value. "
					+ "Returns a human-readable status message."
			)
	public String deleteByAttribute(
			@McpToolParam(description = "XMI model file name, e.g., Family.xmi", required = true)
			String modelFileName,

			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName,

			@McpToolParam(description = "EClass name, e.g., Person", required = true)
			String className,

			@McpToolParam(description = "Attribute name to match, e.g., firstName", required = true)
			String attribute,

			@McpToolParam(description = "Value to match (instances with attribute==value will be deleted)", required = true)
			String value
			) {
		boolean deleted = services.deleteInstancesByAttribute(
				packageName, className, attribute, value, modelFileName
				);

		if (!deleted) {
			throw new IllegalStateException("No matching instances found to delete.");
		}

		return "✅ Deleted matching instance(s).";
	}

	/**
	 * Search instances by attribute
	 */

	@McpTool(
			name = "search_instances_by_attribute",
			description = "Search instances of a given EClass by a single attribute=value filter. Returns matching instances as JSON-like maps."
			)
	public List<Map<String, Object>> searchByAttribute(
			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName,

			@McpToolParam(description = "EClass name, e.g., Person", required = true)
			String className,

			@McpToolParam(description = "Attribute name to match, e.g., firstName", required = true)
			String attribute,

			@McpToolParam(description = "Value to match", required = true)
			String value
			) {
		List<EObject> instances = services.findInstancesOfClassWithAttribute(
				packageName, className, attribute, value
				);

		return instances.stream()
				.map(Services::eObjectToMap)
				.toList();
	}


	/**
	 * Advanced Search instances by attribute
	 */
	@McpTool(
			name = "advanced_search_instances",
			description = "Perform advanced search on instances of a given EClass using multiple query parameters (e.g., firstName=John, lastName_contains=Smith). Returns matching instances as JSON-like maps."
			)
	public List<Map<String, Object>> searchByAttributes(
			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName,

			@McpToolParam(description = "EClass name, e.g., Person", required = true)
			String className,

			@McpToolParam(
					description = "Map of search parameters. Example: {\"firstName\":\"John\", \"lastName_contains\":\"Smith\"}",
					required = true
					)
			Map<String, String> params
			) {
		List<EObject> instances = services.findInstancesWithAttributesAdvanced(
				packageName, className, params
				);

		return instances.stream()
				.map(Services::eObjectToMap)
				.toList();
	}



	/**------------------------------------------------------------------------------
	 * METAMODEL OPERATIONS
	 * ------------------------------------------------------------------------------
	 */	

	/**
	 * List Packages
	 */
	@McpTool(
			name = "list_packages",
			description = "List all registered/loaded EPackages available in the repository (name, nsURI, etc.)."
			)
	public List<Map<String, String>> listPackages() {
		return services.getAllRegisteredEPackages();
	}

	/**
	 * List classes in package
	 */
	@McpTool(
			name = "list_classes_in_package",
			description = "List all EClass names within a given EPackage."
			)
	public List<String> listClasses(
			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName
			) {
		List<EClass> eClasses = services.getAllEClassesFromPackage(packageName);
		return eClasses.stream().map(EClass::getName).collect(Collectors.toList());
	}

	/**
	 * Get metamodel details
	 */
	@McpTool(
			name = "get_metamodel_details",
			description = "Get metamodel (EPackage) details, including enumerations. Returns a JSON-like map."
			)
	public Map<String, Object> getDetails(
			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName
			) {
		Map<String, Object> result = services.getMetamodelStructureEnumeration(packageName);
		if (result.containsKey("error")) {
			throw new IllegalArgumentException(String.valueOf(result.get("error")));
		}
		return result;
	}


	/**
	 * Create metamodel from scratch
	 */
	@McpTool(
			name = "create_metamodel",
			description = "Create a new EPackage (metamodel) from scratch, register it, and save it to the repository."
			)
	public String createMetamodel(
			@McpToolParam(description = "EPackage name (e.g., CleanWatts)", required = true)
			String name,

			@McpToolParam(description = "Namespace prefix (e.g., cleanwatts)", required = true)
			String nsPrefix,

			@McpToolParam(description = "Namespace URI (e.g., http://www.cleanwatts/v1)", required = true)
			String nsURI
			) {
		try {
			EPackage pkg = services.createAndRegisterEPackage(name, nsPrefix, nsURI);

			File targetDir = new File(RepositoryScanner.REPO_PATH); // uses REPOSITORY_DIR env var if set
			services.saveEPackageToFile(pkg, targetDir.getAbsolutePath());

			return "✅ EPackage created, registered, and saved.";
		} catch (IOException e) {
			throw new IllegalStateException("Failed to save EPackage: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create EPackage: " + e.getMessage(), e);
		}
	}


	/**
	 * Get class details
	 */
	@McpTool(
			name = "get_class_details",
			description = "Get details of a specific EClass within a package (attributes, references, etc.)."
			)
	public Map<String, Object> getClassDetails(
			@McpToolParam(description = "EPackage name, e.g., Family", required = true)
			String packageName,

			@McpToolParam(description = "EClass name, e.g., Person", required = true)
			String className
			) {
		return services.getEClassDetailsFromPackage(packageName, className);
	}


	/**
	 * Create Class
	 */
	@McpTool(
			name = "create_class",
			description = "Create a new EClass in the specified EPackage."
			)
	public String createClass(
			@McpToolParam(description = "EPackage name where the class will be created", required = true)
			String packageName,

			@McpToolParam(description = "New EClass name", required = true)
			String className
			) {
		boolean created = services.createEClass(packageName, className);
		if (!created) {
			throw new IllegalArgumentException("Could not create EClass: " + className);
		}
		return "✅ EClass has been created successfully";
	}

	/**
	 * Delete a class
	 */
	@McpTool(
			name = "delete_class",
			description = "Delete an existing EClass from the specified EPackage."
			)
	public String deleteClass(
			@McpToolParam(description = "EPackage name containing the class", required = true)
			String packageName,

			@McpToolParam(description = "EClass name to delete", required = true)
			String className
			) {
		boolean deleted = services.deleteEClass(packageName, className);
		if (!deleted) {
			throw new IllegalStateException("Class not found: " + className);
		}
		return "✅ Class deleted successfully.";
	}


	/**
	 * Create an attribute
	 */
	@McpTool(
			name = "add_attribute",
			description = "Add an EAttribute to a given EClass in an EPackage."
			)
	public String addAttribute(
			@McpToolParam(description = "EPackage name", required = true) String packageName,
			@McpToolParam(description = "EClass name", required = true) String className,
			@McpToolParam(description = "New attribute name", required = true) String attributeName,
			@McpToolParam(description = "Attribute type (e.g., EString, EInt, EBoolean, Double, etc.)", required = true)
			String attributeType
			) {
		boolean added = services.addAttributeToEClass(packageName, className, attributeName, attributeType);
		if (!added) {
			throw new IllegalArgumentException("Could not add attribute: " + attributeName + ":" + attributeType);
		}
		return "✅ Attribute created successfully: " + attributeName + ":" + attributeType;
	}

	/**
	 * Delete an attribute
	 */
	@McpTool(
			name = "delete_attribute",
			description = "Delete an existing EAttribute from a given EClass in an EPackage."
			)
	public String deleteAttribute(
			@McpToolParam(description = "EPackage name", required = true) String packageName,
			@McpToolParam(description = "EClass name", required = true) String className,
			@McpToolParam(description = "Attribute name to delete", required = true) String attributeName
			) {
		boolean deleted = services.deleteAttributeFromEClass(packageName, className, attributeName);
		if (!deleted) {
			throw new IllegalStateException("Could not find/delete attribute: " + attributeName);
		}
		return "✅ Attribute deleted successfully: " + attributeName;
	}


	/**
	 * Rename an attribute
	 */
	@McpTool(
			name = "rename_attribute",
			description = "Rename an existing EAttribute in a given EClass in an EPackage."
			)
	public String renameAttribute(
			@McpToolParam(description = "EPackage name", required = true) String packageName,
			@McpToolParam(description = "EClass name", required = true) String className,
			@McpToolParam(description = "Current attribute name", required = true) String attributeName,
			@McpToolParam(description = "New attribute name", required = true) String newName
			) {
		boolean renamed = services.renameAttributeInEClass(packageName, className, attributeName, newName);
		if (!renamed) {
			throw new IllegalStateException("Could not rename attribute: " + attributeName);
		}
		return "✅ Attribute renamed successfully: " + attributeName + " -> " + newName;
	}

	/**
	 * Add a reference
	 */
	@McpTool(
			name = "add_reference",
			description = "Add an EReference to a source EClass in a given EPackage (optionally containment and/or many)."
			)
	public String addReference(
			@McpToolParam(description = "EPackage name", required = true) String packageName,
			@McpToolParam(description = "Source EClass name (the class that will own the reference)", required = true) String sourceClassName,
			@McpToolParam(description = "Target EClass name (the reference type)", required = true) String targetClassName,
			@McpToolParam(description = "Reference name", required = true) String name,
			@McpToolParam(description = "True if containment reference, false otherwise", required = true) boolean containment,
			@McpToolParam(description = "True for multiplicity many (*), false for single (1)", required = true) boolean many
			) {
		boolean added = services.addReferenceToEClass(
				packageName, sourceClassName, targetClassName, name, containment, many
				);

		if (!added) {
			throw new IllegalArgumentException("Could not create EReference: " + name);
		}
		return "✅ EReference created and .ecore saved.";
	}


	/**
	 * Delete a reference
	 */
	@McpTool(
			name = "delete_reference",
			description = "Delete an EReference from a given EClass in an EPackage."
			)
	public String deleteReference(
			@McpToolParam(description = "EPackage name", required = true) String packageName,
			@McpToolParam(description = "EClass name containing the reference", required = true) String className,
			@McpToolParam(description = "Reference name to delete", required = true) String referenceName
			) {
		boolean success = services.deleteReference(packageName, className, referenceName);

		if (!success) {
			throw new IllegalStateException("Failed to delete reference: " + referenceName);
		}
		return "✅ Reference deleted successfully.";
	}

	/**------------------------------------------------------------------------------
	 *LIST PACKAGES WITH THEIR CORRESPONDING MODELS
	 * ------------------------------------------------------------------------------
	 */
	@McpTool(
			name = "list_packages_with_models",
			description = "List all discovered EPackages and the XMI model files associated to each package (grouped by nsURI)."
			)
	public List<Map<String, Object>> listPackagesWithModels() {
		//return services.listPackagesWithXmiFiles();
		return PerfUtils.timed("list_packages_with_models", () -> services.listPackagesWithXmiFiles());
	}

	/**
	 * Create a model from scratch
	 */

	@McpTool(
			name = "create_model",
			description = "Create a new XMI model file for a given metamodel/package and root class. "
					+ "If fileName is omitted, a timestamped name is generated."
			)
	public String createModel(
			@McpToolParam(description = "Package name (or nsURI/nsPrefix). Default: CWUseCase", required = false)
			String packageName,

			@McpToolParam(description = "Root EClass name. Default: House", required = false)
			String rootClassName,

			@McpToolParam(description = "XMI file name (e.g., MyModel.xmi). Optional", required = false)
			String fileName
			) {
		String pkg = (packageName == null || packageName.isBlank()) ? "CWUseCase" : packageName;
		String root = (rootClassName == null || rootClassName.isBlank()) ? "House" : rootClassName;
		String file = (fileName == null || fileName.isBlank())
				? pkg + "_" + System.currentTimeMillis() + ".xmi"
						: fileName;

		boolean created = services.createNewXmiFile(pkg, root, file);
		if (!created) {
			throw new IllegalArgumentException("Failed to create file: " + file);
		}
		return "✅ Created file: " + file;
	}
	
	
	/**
	 * Create opposite references
	 */
	@McpTool(
		    name = "create_opposite_references",
		    description = "Create a pair of opposite EReferences between two EClasses in a given EPackage."
		)
		public String createOppositeReferences(
		    @McpToolParam(description = "EPackage name", required = true) String packageName,
		    @McpToolParam(description = "Source EClass name", required = true) String sourceClassName,
		    @McpToolParam(description = "Target EClass name", required = true) String targetClassName,
		    @McpToolParam(description = "Reference name from source -> target", required = true) String refNameA,
		    @McpToolParam(description = "Reference name from target -> source", required = true) String refNameB,
		    @McpToolParam(description = "True if the source->target reference is containment", required = true) boolean containment,
		    @McpToolParam(description = "True if multiplicity is many (*) for both references, false for single (1)", required = true) boolean many
		) {
		    boolean success = services.createOppositeReferences(
		        packageName, sourceClassName, targetClassName, refNameA, refNameB, containment, many
		    );

		    if (!success) {
		        throw new IllegalArgumentException("Failed to create opposite references.");
		    }
		    return "✅ Opposite EReferences created.";
		}
	
	/**
	 * List JSON Files
	 */
	@McpTool(
		    name = "list_json_files",
		    description = "List all .json files available in the repository (optionally scanning subfolders)."
		)
		public List<Map<String, Object>> listJsonFiles(
		    @McpToolParam(
		        description = "If true, scan subfolders as well. Default is false.",
		        required = false
		    )
		    Boolean recursive
		) {
		    boolean rec = (recursive != null) && recursive;
		    return services.listJsonFiles(rec);
		}
	
	
	/**
	 * GET/READ Json Content
	 */
	@McpTool(
	        name = "read_json_file",
	        description = "Read a JSON file from the repository and return its parsed content (object or array)."
	    )
	    public Object readJsonContent(
	        @McpToolParam(
	            description = "JSON file name in the repository (e.g., sysml_model.json). Extension optional.",
	            required = true
	        )
	        String jsonFileName
	    ) {
	        return services.readJsonFileContent(jsonFileName);
	    }
	
	/**------------------------------------------------------------------------------
	 * TRANSFORMATION USING LEGACY JAVA
	 * ------------------------------------------------------------------------------
	 */
	
	
	/**
	 * Transform sysml model (.xmi) to JSON text using legacy java (No EOL or ETL)
	 */
	@McpTool(
		    name = "sysml_model_to_sysml_json_legacy",
		    description = "Transform an XMI model file to JSON and save the JSON output in the repository. Returns metadata such as output file name (and optionally the JSON content)."
		)
		public Map<String, Object> transformXmiToJson(
		    @McpToolParam(
		        description = "XMI model file name in the repository (e.g., VehicleDefinitions.xmi).",
		        required = true
		    )
		    String modelFileName
		) {
		    return services.transformXmiToJsonAndSave(modelFileName);
		}
	
	/**------------------------------------------------------------------------------
	 * MODEL TRANSFORMATION
	 * ------------------------------------------------------------------------------
	 */
	
	/**
	 * M2T TRANSFORMATION USING EOL AND EGL
	 */
	/*
	@McpTool(
		    name = "m2t_sysml_model_to_syml_json",
		    description = "Reads a SysML model in XMI format from the repository, executes a model-to-text transformation using EOL and EGL, generates JSON text, and saves the output file into the repository epsilon subfolder. Optionally accepts a custom output file name."
		)
		public Map<String, Object> m2tSysmlToJsonText(
		        String xmiFileName,
		        String outputFileName) throws Exception {
		    return exportService.exportSysmlXmiToJsonAndSave(xmiFileName, outputFileName);
		}
	*/
	
	
	
	
	/**
	 * M2M TRANSFORMATION USING ETL
	 */
	@McpTool(
		    name = "m2m_sysml_model_to_json_model",
		    description = "Model to Model Transformation. Reads a SysML model in XMI format from the repository, executes a model-to-model transformation using ETL, transforms it into a JSON model in XMI format, and saves the generated target model into the repository epsilon subfolder. Optionally accepts a custom output file name."
		)
		public Map<String, Object> m2mTransformation(
		        String xmiFileName,
		        String outputFileName) throws Exception {
		    return m2mtransformation.transformSysmlToJsonModel(xmiFileName, outputFileName);
		}
	
	
	/**
	 * Model Complexity
	 */
	@McpTool(
		    name = "model_analysis",
		    description = "Reads a SysML model in XMI format from the repository and computes complexity metrics using EOL/EGL. The analysis currently returns metrics such as namespace count, total semantic elements, relationship count, maximum model depth, and an overall complexity score."
		)
		public Map<String, Object> analyzeSysmlComplexity(String xmiFileName) throws Exception {
		    return complexityService.analyzeSysmlComplexity(xmiFileName);
		}
	
	
	
	
}
