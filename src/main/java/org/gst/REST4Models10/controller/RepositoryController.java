package org.gst.REST4Models10.controller;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.gst.REST4Models10.repository.RepositoryScanner;
import org.gst.REST4Models10.service.Services;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/repository")
@Tag(name = "Repository", description = "Repository scanning and package/meta-model discovery")
public class RepositoryController {
	
	private final Services scannerService;

    public RepositoryController(Services scannerService) {
        this.scannerService = scannerService;
    }

    /**--------List packages and their corresponding models-------
 	* ------------------------------------------------------------
 	*/
    @Operation(
        summary = "List packages and their XMI files",
        description = "Returns all discovered EPackages and the XMI model files associated to each package (by nsURI)."
    )
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> getPackagesWithModels() {
        return ResponseEntity.ok(scannerService.listPackagesWithXmiFiles());
    }
    

 	/**-------------Create an XMI File-------------------------
 	* ---------------------------------------------------------
 	*/
 	public record CreateModelRequest(String packageName, String rootClassName, String fileName) {}

 	@Operation(summary = "Create a new XMI file")
 	@PostMapping
 	public ResponseEntity<String> createNewXmi(@RequestBody(required = false) CreateModelRequest req) {
 		String pkg = (req == null || req.packageName() == null || req.packageName().isBlank()) ? "CWUseCase" : req.packageName();
 		String root = (req == null || req.rootClassName() == null || req.rootClassName().isBlank()) ? "House" : req.rootClassName();
 		String file = (req == null || req.fileName() == null || req.fileName().isBlank())
 				? pkg + "_" + System.currentTimeMillis() + ".xmi"
 						: req.fileName();

 		boolean created = scannerService.createNewXmiFile(pkg, root, file);
 		return created
 				? ResponseEntity.ok("✅ Created file: " + file)
 						: ResponseEntity.badRequest().body("❌ Failed to create file.");
 	}
 	
 	/**-------------List JSON files in repository-------------------------
 	* ---------------------------------------------------------
 	*/
 	@Operation(
 	        summary = "List JSON files in repository",
 	        description = "Returns all .json files found in the repository directory. Set recursive=true to include subfolders."
 	    )
 	    @GetMapping("/json-files")
 	    public ResponseEntity<List<Map<String, Object>>> listJsonFiles(
 	            @RequestParam(defaultValue = "false") boolean recursive
 	    ) {
 	        return ResponseEntity.ok(scannerService.listJsonFiles(recursive));
 	    }
 	
 	
 	/**
 	 * Upload a model
 	 */
 	@Operation(
 	        summary = "Upload a model file",
 	        description = "Upload a model file in the repository"
 	    )
 	@PostMapping(
 		    value = "/upload",
 		    consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE
 		)
 		public ResponseEntity<Map<String, Object>> uploadModel(
 		        @RequestParam("file") MultipartFile file
 		) {
 		    try {
 		        File repoDir = new File(RepositoryScanner.REPO_PATH);
 		        if (!repoDir.exists() || !repoDir.isDirectory()) {
 		        	return ResponseEntity.status(500).body(Map.of(
 		        			"error", "Repository directory not found",
 		        	        "repoPath", repoDir.getAbsolutePath()
 		            ));
 		        }

 		        String original = file.getOriginalFilename();
 		        if (original == null || original.isBlank()) {
 		            return ResponseEntity.badRequest().body(Map.of("error", "Missing filename"));
 		        }

 		        // Basic filename hardening (prevents path traversal like ../../something)
 		        String filename = new File(original).getName();

 		        if (file.isEmpty()) {
 		            return ResponseEntity.badRequest().body(Map.of(
 		                "error", "Uploaded file is empty",
 		                "fileName", filename
 		            ));
 		        }

 		        File target = new File(repoDir, filename);
 		        file.transferTo(target);

 		        // Re-scan to register immediately (fine for now; later you can optimize)
 		        new RepositoryScanner().scanRepository(repoDir);

 		        return ResponseEntity.ok(Map.of(
 		            "savedAs", filename,
 		            "path", target.getAbsolutePath(),
 		            "sizeBytes", target.length()
 		        ));
 		    } catch (Exception e) {
 		        return ResponseEntity.status(500).body(Map.of(
 		            "error", "Upload failed",
 		            "message", e.getMessage()
 		        ));
 		    }
 		}
 	
 	
 	/**
 	 * GET JSON FILE CONTENT FROM THE REPO
 	 * 
 	 */
 	@Operation(
 	        summary = "Read JSON file content from repository",
 	        description = "Reads a .json file from the repository and returns the parsed JSON (object or array)."
 	    )
 	    @GetMapping("/json/{jsonFileName:.+}")
 	    public ResponseEntity<Object> readJsonFile(
 	            @PathVariable String jsonFileName
 	    ) {
 	        return ResponseEntity.ok(scannerService.readJsonFileContent(jsonFileName));
 	    }


 	/**
 	 * Read the EXACT stored bytes of a repository file (byte-preserving raw GET).
 	 *
 	 * Symmetric inverse of POST /upload: streams the verbatim bytes stored under
 	 * RepositoryScanner.REPO_PATH with NO EMF/JSON parsing or expansion, so a
 	 * store->get round-trip is byte-identical. Filename is hardened with
 	 * new File(name).getName() (same idiom as uploadModel) to prevent path
 	 * traversal; 404 when the file is absent; application/octet-stream body.
 	 */
 	@Operation(
 	        summary = "Read raw file bytes from repository",
 	        description = "Streams the exact stored bytes of a repository file (application/octet-stream), no parsing or expansion. Byte-preserving inverse of /upload."
 	    )
 	    @GetMapping("/raw/{filename:.+}")
 	    public ResponseEntity<byte[]> readRawFile(
 	            @PathVariable String filename
 	    ) {
 	        // Basic filename hardening (prevents path traversal like ../../something)
 	        String hardened = new File(filename).getName();
 	        File target = new File(RepositoryScanner.REPO_PATH, hardened);
 	        if (!target.exists() || !target.isFile()) {
 	            return ResponseEntity.notFound().build();
 	        }
 	        try {
 	            byte[] bytes = java.nio.file.Files.readAllBytes(target.toPath());
 	            return ResponseEntity.ok()
 	                    .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
 	                    .body(bytes);
 	        } catch (Exception e) {
 	            return ResponseEntity.status(500).build();
 	        }
 	    }


}
