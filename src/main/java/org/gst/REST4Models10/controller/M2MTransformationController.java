package org.gst.REST4Models10.controller;

import java.util.Map;

import org.gst.REST4Models10.service.M2MTransformationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/transformation")
@Tag(name = "Transformations (EOL & ETL)", description = "M2T Transformation (EOL) & M2M Transformation (ETL)" )
public class M2MTransformationController {

    private final M2MTransformationService transformationService;

    public M2MTransformationController(M2MTransformationService transformationService) {
        this.transformationService = transformationService;
    }

    /**
     * Execute the model-to-model transformation:
     * SysML XMI -> JSON model (XMI)
     *
     * Example:
     * GET /sysml/m2m-json?xmiFileName=Example.xmi
     * GET /sysml/m2m-json?xmiFileName=Example.xmi&outputFileName=Example_jsonmodel.xmi
     */
    @Operation(summary = "M2M Transformation (SysML Model (XMI) -> JSON model (XMI) using ETL")
    @GetMapping("/m2m-sysml-model-to-json-model")
    public ResponseEntity<Map<String, Object>> transformSysmlToJsonModel(
            @RequestParam String xmiFileName,
            @RequestParam(required = false) String outputFileName) throws Exception {

        return ResponseEntity.ok(transformationService.transformSysmlToJsonModel(xmiFileName, outputFileName));
    }
    
    
    
}