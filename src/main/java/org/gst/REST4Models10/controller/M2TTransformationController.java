package org.gst.REST4Models10.controller;
import java.util.Map;

import org.gst.REST4Models10.service.M2TTransformationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/transformation")
@Tag(name = "Transformations (EOL & ETL)", description = "M2T Transformation (EOL) & M2M Transformation (ETL)")
public class M2TTransformationController {

    private final M2TTransformationService exportService;
    

    public M2TTransformationController(M2TTransformationService exportService) {
        this.exportService = exportService;
    }
    
    /**
     * ------------------SysML(xmi) --> SysML(json)-----------------------------
     */
    /*
    @Operation(summary = "Transform a sysml model (.xmi) into sysml (.json) using EOL and EGL")
    @GetMapping("/m2t-sysml-to-sysml-json")
    public ResponseEntity<Map<String, Object>> exportSysmlToJsonAndSave(
            @RequestParam String xmiFileName,
            @RequestParam(required = false) String outputFileName) throws Exception {

        return ResponseEntity.ok(exportService.exportSysmlXmiToJsonAndSave(xmiFileName, outputFileName));
    }
    */
    
    
  
  
    
    
    
}
