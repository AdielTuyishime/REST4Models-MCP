package org.gst.REST4Models10.controller;

import java.util.Map;

import org.gst.REST4Models10.service.ModelAnalysis;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/analysis")
@Tag(name = "Model Analysis", description = "Given a sysml model (xmi) calculate the complexity" )
public class ModelAnalysisController {
	private final ModelAnalysis complexityService;

    public ModelAnalysisController(ModelAnalysis complexityService) {
        this.complexityService = complexityService;
    }

    /**
     *  SYSML MODEL COMPLEXITY SCORE
     * Execute the EOL-based complexity analysis for a SysML XMI model.
     *
     * Example:
     * GET /sysml/complexity?xmiFileName=ItemsExample.xmi
     */
   
    @Operation(summary = "Model analysis: compute SysML complexity metrics using EOL")
    @GetMapping("/complexity")
    public ResponseEntity<Map<String, Object>> analyzeSysmlComplexity(
            @RequestParam String xmiFileName) throws Exception {

        return ResponseEntity.ok(
                complexityService.analyzeSysmlComplexity(xmiFileName)
        );
    }

    
}
