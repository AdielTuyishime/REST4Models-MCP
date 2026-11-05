package org.gst.REST4Models10.controller;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.gst.REST4Models10.repository.RepositoryScanner;
import org.gst.REST4Models10.service.Services;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/metamodels")
@Tag(name = "Metamodels", description = "Metamodel introspection and editing")
public class MetamodelController {

	private final Services scannerService;

	public MetamodelController(Services scannerService) {
		this.scannerService = scannerService;
	}

	// -------- Packages --------
	
	/**----------------List of Packages --------------
     * Get a list of packages in the repository
     * -----------------------------------------------
     */
	@Operation(summary = "List loaded packages")
	@GetMapping
	public ResponseEntity<List<Map<String, String>>> listPackages() {
		return ResponseEntity.ok(scannerService.getAllRegisteredEPackages());
	}

	/**----------------Create Epackage/Metamodel from Scratch----------------
	 * ------------------------------------------------
     */
	public record CreatePackageRequest(String name, String nsPrefix, String nsURI) {}

	@Operation(summary = "Create a package")
	@PostMapping
	public ResponseEntity<String> createPackage(@RequestBody CreatePackageRequest req) {
		try {
			EPackage pkg = scannerService.createAndRegisterEPackage(req.name(), req.nsPrefix(), req.nsURI());
			File targetFile = new File(RepositoryScanner.REPO_PATH);
			scannerService.saveEPackageToFile(pkg, targetFile.getAbsolutePath());
			return ResponseEntity.ok("✅ EPackage created, registered, and saved.");
		} catch (IOException e) {
			return ResponseEntity.status(500).body("❌ Failed to save EPackage: " + e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(500).body("❌ Failed to create EPackage: " + e.getMessage());
		}
	}

	
	// -------- Classes --------
	
	/**--------------Get Classes within a Package -------------
	* ---------------------------------------------------------
	*/

	@Operation(summary = "List classes within a package")
	@GetMapping("/{packageName}/classes")
	public List<String> listClasses(@PathVariable String packageName) {
		List<EClass> eClasses = scannerService.getAllEClassesFromPackage(packageName);
		return eClasses.stream().map(EClass::getName).collect(Collectors.toList());
	}
	
	/**----Get Details of a specific Eclass (attr, references, etc)---
	* ----------------------------------------------------------------
	*/
	@Operation(summary = "Get class details")
	@GetMapping("/{packageName}/{className}/details")
	public ResponseEntity<Map<String, Object>> getClassDetails(
			@PathVariable String packageName,
			@PathVariable String className
			) {
		return ResponseEntity.ok(scannerService.getEClassDetailsFromPackage(packageName, className));
	}

	
	/**--------------Create EClasses -------------
	* --------------------------------------------
	*/
	@Operation(summary = "Create a class in a package")
	@PostMapping("/{packageName}/class")
	public ResponseEntity<String> createClass(
	        @PathVariable String packageName,
	        @RequestParam String className
	) {
	    boolean created = scannerService.createEClass(packageName, className);
	    return created
	            ? ResponseEntity.ok("✅ EClass has been created successfully")
	            : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Could not create EClass: " + className);
	}

	/**--------------Delete an EClass ----------------
	* ------------------------------------------------
	*/
	@Operation(summary = "Delete a class from a package")
	@DeleteMapping("/{packageName}/classes/{className}")
	public ResponseEntity<String> deleteClass(
			@PathVariable String packageName,
			@PathVariable String className
			) {
		boolean deleted = scannerService.deleteEClass(packageName, className);
		return deleted
				? ResponseEntity.ok("✅ Class deleted successfully.")
						: ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Class not found.");
	}

	
	// -------- Structure / Details / Summary --------
	
	/**-------------Get the details of a metamodel-------------
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Get metamodel structure (classes + attributes + references)")
	@GetMapping("/{packageName}/structure")
	public ResponseEntity<List<Map<String, Object>>> getStructure(@PathVariable String packageName) {
		List<Map<String, Object>> result = scannerService.getMetamodelStructure(packageName);
		if (result.isEmpty()) {
			return ResponseEntity.status(404).body(List.of(Map.of("error", "Package not found or contains no classes")));
		}
		return ResponseEntity.ok(result);
	}

	@Operation(summary = "Get metamodel details (includes enumerations)")
	@GetMapping("/{packageName}/details")
	public ResponseEntity<Map<String, Object>> getDetails(@PathVariable String packageName) {
		Map<String, Object> result = scannerService.getMetamodelStructureEnumeration(packageName);
		if (result.containsKey("error")) return ResponseEntity.status(404).body(result);
		return ResponseEntity.ok(result);
	}

	/**-------------Statistics(no.class, attributes,etc)-------
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Get metamodel summary (counts)")
	@GetMapping("/{packageName}/summary")
	public ResponseEntity<Map<String, Integer>> getSummary(@PathVariable String packageName) {
		return ResponseEntity.ok(scannerService.countElementsInPackage(packageName));
	}
	

	// -------- Attributes --------
	
	/**-------------Create an attributes-----------------------
	* ---------------------------------------------------------
	*/
	public record AddAttributeRequest(String name, String type) {}
	public record RenameAttributeRequest(String newName) {}

	@Operation(summary = "Add an attribute to a specific class")
	@PostMapping("/{packageName}/{className}/attribute")
	public ResponseEntity<String> addAttribute(
			@PathVariable String packageName,
			@PathVariable String className,
			@RequestParam String attributeName,
	        @RequestParam String attributeType
			) {
		boolean added = scannerService.addAttributeToEClass(packageName, className, attributeName,
	            attributeType);
		return added
				? ResponseEntity.ok("✅ Attribute created successfully:" + attributeName+":"+attributeType)
						: ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Could not add attribute.");
	}

	/**-------------Delete an attribute------------------------
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Delete an attribute of a specific class")
	@DeleteMapping("/{packageName}/{className}/attributes/{attributeName}")
	public ResponseEntity<String> deleteAttribute(
			@PathVariable String packageName,
			@PathVariable String className,
			@PathVariable String attributeName
			) {
		boolean deleted = scannerService.deleteAttributeFromEClass(packageName, className, attributeName);
		return deleted
				? ResponseEntity.ok("✅ Attribute deleted successfully:"  + attributeName)
						: ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Could not find/delete attribute: " + attributeName);
	}

	/**-------------Rename an attribute------------------------
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Rename an attribute of a specific class")
	@PutMapping("/{packageName}/{className}/attributes/{attributeName}")
	public ResponseEntity<String> renameAttribute(
			@PathVariable String packageName,
			@PathVariable String className,
			@PathVariable String attributeName,
			@RequestBody RenameAttributeRequest req
			) {
		boolean renamed = scannerService.renameAttributeInEClass(packageName, className, attributeName, req.newName());
		return renamed
				? ResponseEntity.ok("✅ Attribute renamed successfully:" + attributeName)
						: ResponseEntity.status(HttpStatus.NOT_FOUND).body("❌ Could not rename attribute: " + attributeName);
	}

	// -------- References --------

	/**------------add a reference----------------------------
	* ---------------------------------------------------------
	*/
	public record AddReferenceRequest(
			String targetClassName,
			String name,
			boolean containment,
			boolean many
			) {}

	@Operation(summary = "Add a reference")
	@PostMapping("/{packageName}/{sourceClassName}/references")
	public ResponseEntity<String> addReference(
			@PathVariable String packageName,
			@PathVariable String sourceClassName,
			@RequestBody AddReferenceRequest req
			) {
		boolean added = scannerService.addReferenceToEClass(
				packageName,
				sourceClassName,
				req.targetClassName(),
				req.name(),
				req.containment(),
				req.many()
				);
		return added
				? ResponseEntity.ok("✅ EReference created and .ecore saved.")
						: ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Could not create EReference.");
	}

	/**-------------Delete a reference-------------------------
	* ---------------------------------------------------------
	*/
	@Operation(summary = "Delete a reference")
	@DeleteMapping("/{packageName}/{className}/references/{referenceName}")
	public ResponseEntity<String> deleteReference(
			@PathVariable String packageName,
			@PathVariable String className,
			@PathVariable String referenceName
			) {
		boolean success = scannerService.deleteReference(packageName, className, referenceName);
		return success
				? ResponseEntity.ok("✅ Reference deleted successfully.")
						: ResponseEntity.status(404).body("❌ Failed to delete reference.");
	}

	/**-------------Create an Eopposite Reference--------------
	* ---------------------------------------------------------
	*/
	public record OppositeReferencesRequest(
			String sourceClassName,
			String targetClassName,
			String refNameA,
			String refNameB,
			boolean containment,
			boolean many
			) {}

	@Operation(summary = "Create opposite references")
	@PostMapping("/{packageName}/references/opposites")
	public ResponseEntity<String> createOpposites(
			@PathVariable String packageName,
			@RequestBody OppositeReferencesRequest req
			) {
		boolean success = scannerService.createOppositeReferences(
				packageName,
				req.sourceClassName(),
				req.targetClassName(),
				req.refNameA(),
				req.refNameB(),
				req.containment(),
				req.many()
				);
		return success
				? ResponseEntity.ok("✅ Opposite EReferences created.")
						: ResponseEntity.status(HttpStatus.BAD_REQUEST).body("❌ Failed to create opposite references.");
	}

}
