package org.gst.REST4Models10.controller;

import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.gst.REST4Models10.service.Services;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Models", description = "XMI model operations")
public class ModelController {

	private final Services scannerService;

	public ModelController(Services scannerService) {
		this.scannerService = scannerService;
	}

	// -------- Read XMI as JSON --------
	
	/**--------Get the content of a model---------------------
	* ---------------------------------------------------------
	*/
	@Operation(
			summary = "Read model content",
			description = "Returns root object(s) and recursively expands containment references. Non-containment references are returned as lightweight _ref."
			)
	@GetMapping("/{modelFileName:.+}/content")
	public ResponseEntity<List<Map<String, Object>>> readXmiAsJson(@PathVariable String modelFileName) {
		return ResponseEntity.ok(scannerService.readXmiAsJson(modelFileName));
	}
	
	// -------- Instances in a specific model --------
	
	/**-------------Get an instance of a specific Class--------
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Get instances of a class in a specific model file")
	@GetMapping("/{modelFileName:.+}/{packageName}/{className}/instances")
	public ResponseEntity<List<Map<String, Object>>> getInstancesInModel(
			@PathVariable String modelFileName,
			@PathVariable String packageName,
			@PathVariable String className
			) {
		List<EObject> instances = scannerService.getInstancesOfClassFromSpecificFile(packageName, className, modelFileName);
		List<Map<String, Object>> mapped = instances.stream().map(Services::eObjectToMapClass).toList();
		return ResponseEntity.ok(mapped);
	}
	
	
	/**---Get all instance of a specific Class conform to the same metamodel-----
	* ---------------------------------------------------------------------------
	*/
	@Operation(summary = "Get all instances of model(s) conform to the same metamodel",
			description = "Return all instances of a class")
 	@GetMapping("/{packageName}/{className}/instances/all")
 	public ResponseEntity<List<Map<String, Object>>> getAllInstances(
 	    @PathVariable String packageName,
 	    @PathVariable String className
 	) {
 	    List<EObject> instances = scannerService.getInstancesOfClass(packageName, className);
 	   List<Map<String, Object>> mapped = instances.stream()
 			    .map(Services::eObjectToMap) // Ensure eObjectToMap is accessible here
 			    .toList();
 			
 	    
 			mapped.forEach(item -> {
 		    item.forEach((key, val) -> {
 		        if (val instanceof EObject) {
 		            System.err.println("⚠️ Still contains EObject for key: " + key);
 		        }
 		    });
 		});
 	    
 	    System.out.println("🔎 Result to be returned: " + mapped);
 	    return ResponseEntity.ok(mapped);
 	}
	

	/**-------------Create an instance in a specific class-----
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Create an instance of a class in a specific model file (attributes in JSON body)")
	@PostMapping("/{modelFileName:.+}/{packageName}/{className}/instances")
	public ResponseEntity<String> createInstance(
			@PathVariable String modelFileName,
			@PathVariable String packageName,
			@PathVariable String className,
			@RequestBody Map<String, String> attributes
			) {
		boolean created = scannerService.insertNewInstance(packageName, className, attributes, modelFileName);
		return created
				? ResponseEntity.ok("✅ New instance inserted and saved.")
						: ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Insert failed.");
	}

	/**-------------Update an instance of a specific class-----
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Update instances by attribute in a specific model file")
	@PutMapping("/{modelFileName:.+}/{packageName}/{className}/instances")
	public ResponseEntity<String> updateByAttribute(
			@PathVariable String modelFileName,
			@PathVariable String packageName,
			@PathVariable String className,
			@RequestParam String attribute,
			@RequestParam String oldValue,
			@RequestParam String newValue
			) {
		boolean updated = scannerService.updateInstancesByAttribute(packageName, className, attribute, oldValue, newValue, modelFileName);
		return updated
				? ResponseEntity.ok("✅ Updated matching instance(s).")
						: ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ No matching instances found.");
	}

	
	/**-------------Delete an instance-------------------------
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Delete instances by attribute in a specific model file")
	@DeleteMapping("/{modelFileName:.+}/{packageName}/{className}/instances")
	public ResponseEntity<String> deleteByAttribute(
			@PathVariable String modelFileName,
			@PathVariable String packageName,
			@PathVariable String className,
			@RequestParam String attribute,
			@RequestParam String value
			) {
		boolean deleted = scannerService.deleteInstancesByAttribute(packageName, className, attribute, value, modelFileName);
		return deleted
				? ResponseEntity.ok("✅ Deleted matching instance(s).")
						: ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ No matching instances found.");
	}

	
	// -------- Insert child into container (model-specific) --------
	
	/**---Add an instance to a specific class which has a parent class---
	* -------------------------------------------------------------------
	*/
	public record InsertChildRequest(
			String parentAttrName,
			String parentAttrValue,
			String containmentRefName,
			String childClass,
			Map<String, String> childAttributes
			) {}

	@Operation(summary = "Insert a child EObject into an existing container in a model")
	@PostMapping("/{modelFileName:.+}/{parentPackage}/{parentClass}/children")
	public ResponseEntity<String> insertChild(
			@PathVariable String modelFileName,
			@PathVariable String parentPackage,
			@PathVariable String parentClass,
			@RequestBody InsertChildRequest req
			) {
		boolean inserted = scannerService.insertIntoContainer(
				parentPackage,
				parentClass,
				req.parentAttrName(),
				req.parentAttrValue(),
				req.containmentRefName(),
				req.childClass(),
				req.childAttributes(),
				modelFileName
				);

		return inserted
				? ResponseEntity.ok("✅ Data inserted successfully")
						: ResponseEntity.badRequest().body("❌ Insert failed.");
	}
	
	
	// ---------Search-Instances---------------------------
	
	/**------------------------------------------------------
     * Searching instances by attribute & value
     * ------------------------------------------------------
     */
    @Operation(summary = "Fetch instances of class with attributes",
		description = "Fetch Instance of Class by attribute and its corresponding value")
    @GetMapping("/{packageName}/{className}/search")
    public ResponseEntity<List<Map<String, Object>>> searchByAttribute(
        @PathVariable String packageName,
        @PathVariable String className,
        @RequestParam String attribute,
        @RequestParam String value
    ) {
        List<EObject> instances = scannerService.findInstancesOfClassWithAttribute(
            packageName, className, attribute, value
        );

        List<Map<String, Object>> mapped = instances.stream()
            .map(Services::eObjectToMap)
            .toList();
        
        System.out.println("🔎 Result to be returned: " + mapped);
        return ResponseEntity.ok(mapped);
    }
    
    
    /**------------------------------------------------------
     * Perform an advanced search
     * -------------------------------------------------------
     * {"firstName": "John","lastName_contains": "Smith"}
     * GET /Family/Person/search?firstName=John, 
     * GET /Family/Person/search?lastName_contains=Smith, 
     * GET /Family/Person/search?firstName=John&lastName_contains=Smith
     */
    @Operation(summary = "Perform advanced search by attributes",
   	description = "")
    @GetMapping("/{packageName}/{className}/advancedSearch")
    public ResponseEntity<List<Map<String, Object>>> searchByAttributes(
        @PathVariable String packageName,
        @PathVariable String className,
        @RequestParam Map<String, String> params
    ) {
        List<EObject> instances = scannerService.findInstancesWithAttributesAdvanced(
            packageName, className, params
        );

        List<Map<String, Object>> mapped = instances.stream()
            .map(Services::eObjectToMap)
            .toList();

        return ResponseEntity.ok(mapped);
    }

}
