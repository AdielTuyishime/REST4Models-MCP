package org.gst.REST4Models10.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.gst.REST4Models10.repository.RepositoryScanner;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GuiController {

	//home page
	@GetMapping("")
	public String docs() {
		return "redirect:/home.html";
	} 


	@GetMapping("/guiModeling")
	public String clientUI() {  	
		return "redirect:/guiModeling.html"; 

	}

	@GetMapping("/DT-Model")
	public String instanceFormPage() {  	
		return "redirect:/model.html"; 

	}

	@GetMapping("/upload")
	public String uploadPage() {
		return "upload";  // This resolves to upload.html
	}
	
	@GetMapping("/documentation")
	public String documenation() {  	
		return "redirect:/documentation.html"; 

	}

	/*
	@PostMapping("/upload")
	public String handleFileUpload(
			@RequestParam(value = "ecoreFile", required = false) MultipartFile ecoreFile,
			@RequestParam(value = "xmiFile", required = false) MultipartFile xmiFile,
			RedirectAttributes redirectAttributes
			) {
		boolean hasEcore = ecoreFile != null && !ecoreFile.isEmpty();
		boolean hasXmi = xmiFile != null && !xmiFile.isEmpty();

		// ❌ Reject if both are uploaded
		if (hasEcore && hasXmi) {
			redirectAttributes.addFlashAttribute("message",
					"⚠️ Please upload only one file at a time — either a .ecore or a .xmi file.");
			return "redirect:/upload";
		}

		// ❌ Reject if neither is uploaded
		if (!hasEcore && !hasXmi) {
			redirectAttributes.addFlashAttribute("message",
					"⚠️ Please upload a .ecore or .xmi file.");
			return "redirect:/upload";
		}

		try {
			// ✅ Handle .ecore file
			if (hasEcore) {
				Path ecorePath = Path.of(RepositoryScanner.REPO_PATH, ecoreFile.getOriginalFilename());
				Files.copy(ecoreFile.getInputStream(), ecorePath, StandardCopyOption.REPLACE_EXISTING);
				redirectAttributes.addFlashAttribute("message",
						"✅ Successfully uploaded .ecore file: " + ecoreFile.getOriginalFilename());
			}

			// ✅ Handle .xmi file
			if (hasXmi) {
				Path xmiPath = Path.of(RepositoryScanner.REPO_PATH, xmiFile.getOriginalFilename());
				Files.copy(xmiFile.getInputStream(), xmiPath, StandardCopyOption.REPLACE_EXISTING);
				redirectAttributes.addFlashAttribute("message",
						"✅ Successfully uploaded .xmi file: " + xmiFile.getOriginalFilename());
			}

			// 🔁 Rescan repository after upload
			new RepositoryScanner().scanRepository(new File(RepositoryScanner.REPO_PATH));

		} catch (IOException e) {
			e.printStackTrace();
			redirectAttributes.addFlashAttribute("message", "❌ Upload failed: " + e.getMessage());
		}

		return "redirect:/upload";
	}*/
	
	@PostMapping("/upload")
	public String handleFileUpload(
	        @RequestParam(value = "files", required = false) List<MultipartFile> files,
	        RedirectAttributes redirectAttributes
	) {
	    // Allowed modeling artifact extensions
	    Set<String> allowedExtensions = Set.of(
	            "xmi", "ecore", "uml", "json", "xml",
	            "aird", "notation", "ocl", "qvto", "etl", "txt", "zip"
	    );

	    if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
	        redirectAttributes.addFlashAttribute("message",
	                "⚠️ Please upload at least one modeling artifact.");
	        return "redirect:/upload";
	    }

	    List<String> uploadedFiles = new ArrayList<>();
	    List<String> rejectedFiles = new ArrayList<>();

	    try {
	        Path repoPath = Path.of(RepositoryScanner.REPO_PATH);
	        Files.createDirectories(repoPath);

	        for (MultipartFile file : files) {
	            if (file == null || file.isEmpty()) {
	                continue;
	            }

	            String originalFilename = file.getOriginalFilename();

	            if (originalFilename == null || originalFilename.isBlank()) {
	                rejectedFiles.add("unknown file");
	                continue;
	            }

	            String cleanedFileName = Paths.get(originalFilename).getFileName().toString();
	            String extension = getExtension(cleanedFileName).toLowerCase();

	            if (!allowedExtensions.contains(extension)) {
	                rejectedFiles.add(cleanedFileName);
	                continue;
	            }

	            Path targetPath = repoPath.resolve(cleanedFileName);
	            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
	            uploadedFiles.add(cleanedFileName);
	        }

	        if (uploadedFiles.isEmpty()) {
	            redirectAttributes.addFlashAttribute("message",
	                    "❌ No valid files were uploaded. Allowed types: " + String.join(", ", allowedExtensions));
	            return "redirect:/upload";
	        }

	        // Rescan repository after upload
	        new RepositoryScanner().scanRepository(new File(RepositoryScanner.REPO_PATH));

	        StringBuilder message = new StringBuilder();
	        message.append("✅ Successfully uploaded: ").append(String.join(", ", uploadedFiles));

	        if (!rejectedFiles.isEmpty()) {
	            message.append(". ⚠️ Rejected unsupported files: ").append(String.join(", ", rejectedFiles));
	        }

	        redirectAttributes.addFlashAttribute("message", message.toString());

	    } catch (IOException e) {
	        e.printStackTrace();
	        redirectAttributes.addFlashAttribute("message", "❌ Upload failed: " + e.getMessage());
	    }

	    return "redirect:/upload";
	}

	private String getExtension(String fileName) {
	    int lastDot = fileName.lastIndexOf('.');
	    return (lastDot == -1 || lastDot == fileName.length() - 1)
	            ? ""
	            : fileName.substring(lastDot + 1);
	}

}
