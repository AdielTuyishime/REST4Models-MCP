package org.gst.REST4Models10.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.eol.models.DefaultRelativePathResolver;
import org.gst.REST4Models10.repository.RepositoryScanner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class M2MTransformationService {

    /**
     * nsURIs for metamodels used in the M2M transformation.
     * Resolved from EPackage.Registry.INSTANCE (populated by RepositoryScanner
     * at startup) to avoid ClassPathResource.getFile() and cross-reference
     * issues when running from a fat JAR.
     *
     * @see <a href="https://github.com/AdielTuyishime/REST4Models/issues/8">Issue #8</a>
     */
    private static final String SYSML_NS_URI = "https://www.omg.org/spec/SysML/20250201";
    private static final String TYPES_NS_URI = "https://www.omg.org/spec/UML/20161101/PrimitiveTypes";
    private static final String JSON_NS_URI  = "http://www.bamol.pt/json/Json";
    

    /**
     * Execute the ETL transformation: M2M Transformation
     * SysML XMI -> JSON model (XMI)
     *
     * The generated target model is saved in repository/epsilon.
     */
    public Map<String, Object> transformSysmlToJsonModel(String xmiFileName, String outputFileName) throws Exception {
        ensureMetamodelsRegistered();

        File sourceFile = resolveRepositoryFile(xmiFileName);
        File etlFile = resolveClasspathResource("epsilon/sysml_model_to_json_model.etl");

        String outName = (outputFileName == null || outputFileName.isBlank())
                ? baseName(xmiFileName) + ".json.xmi"
                : outputFileName;

        if (!outName.toLowerCase().endsWith(".xmi")) {
            outName = outName + ".xmi";
        }

        File repoDir = new File(RepositoryScanner.REPO_PATH);
        File epsilonDir = new File(repoDir, "epsilon");
        if (!epsilonDir.exists()) {
            epsilonDir.mkdirs();
        }

        File targetFile = new File(epsilonDir, outName);

        // Source model: SysML XMI conforming to sysml.ecore (resolved from registry)
        EmfModel sourceModel = new EmfModel();
        StringProperties sourceProps = new StringProperties();
        sourceProps.put(EmfModel.PROPERTY_NAME, "SysML");
        sourceProps.put(EmfModel.PROPERTY_METAMODEL_URI, SYSML_NS_URI);
        sourceProps.put(EmfModel.PROPERTY_MODEL_URI, sourceFile.toURI().toString());
        sourceProps.put(EmfModel.PROPERTY_READONLOAD, "true");
        sourceProps.put(EmfModel.PROPERTY_STOREONDISPOSAL, "false");

        // Target model: empty XMI conforming to json.ecore (resolved from registry)
        EmfModel targetModel = new EmfModel();
        StringProperties targetProps = new StringProperties();
        targetProps.put(EmfModel.PROPERTY_NAME, "JSON");
        targetProps.put(EmfModel.PROPERTY_METAMODEL_URI, JSON_NS_URI);
        targetProps.put(EmfModel.PROPERTY_MODEL_URI, targetFile.toURI().toString());
        targetProps.put(EmfModel.PROPERTY_READONLOAD, "false");
        targetProps.put(EmfModel.PROPERTY_STOREONDISPOSAL, "true");

        EtlModule module = new EtlModule();
        module.parse(etlFile);

        try {
            sourceModel.load(sourceProps, new DefaultRelativePathResolver());
            targetModel.load(targetProps, new DefaultRelativePathResolver());

            module.getContext().getModelRepository().addModel(sourceModel);
            module.getContext().getModelRepository().addModel(targetModel);

            module.execute();
            targetModel.store();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "SysML XMI transformed to JSON model successfully");
            response.put("sourceFile", sourceFile.getName());
            response.put("outputFile", targetFile.getName());
            response.put("outputPath", targetFile.getAbsolutePath());

            return response;
        } finally {
            try {
                sourceModel.dispose();
            } catch (Exception ignored) {
            }
            try {
                targetModel.dispose();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Verify that the required metamodels are registered in the global
     * EPackage registry. These are normally loaded by RepositoryScanner
     * at startup.
     *
     * @throws IllegalStateException if required metamodels are not registered
     * @see <a href="https://github.com/AdielTuyishime/REST4Models/issues/8">Issue #8</a>
     */
    private void ensureMetamodelsRegistered() {
        EPackage sysml = EPackage.Registry.INSTANCE.getEPackage(SYSML_NS_URI);
        EPackage types = EPackage.Registry.INSTANCE.getEPackage(TYPES_NS_URI);
        EPackage json  = EPackage.Registry.INSTANCE.getEPackage(JSON_NS_URI);

        if (sysml == null) {
            throw new IllegalStateException(
                    "SysML metamodel not found in EPackage registry (nsURI: " + SYSML_NS_URI + "). "
                    + "Ensure RepositoryScanner has loaded SysML.ecore at startup.");
        }
        if (types == null) {
            throw new IllegalStateException(
                    "PrimitiveTypes metamodel not found in EPackage registry (nsURI: " + TYPES_NS_URI + "). "
                    + "Ensure RepositoryScanner has loaded types.ecore at startup.");
        }
        if (json == null) {
            throw new IllegalStateException(
                    "JSON metamodel not found in EPackage registry (nsURI: " + JSON_NS_URI + "). "
                    + "Ensure RepositoryScanner has loaded Json.ecore at startup.");
        }
    }

    /**
     * Resolve a classpath resource to a File.
     * Works both from filesystem (IDE, exploded JAR) and from nested fat JAR
     * by extracting the resource to a temporary file when needed.
     */
    private File resolveClasspathResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try {
            return resource.getFile();
        } catch (java.io.FileNotFoundException e) {
            String filename = Paths.get(path).getFileName().toString();
            File tempFile = File.createTempFile("r4m-", "-" + filename);
            tempFile.deleteOnExit();
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        }
    }

    /**
     * Resolve the input XMI file from the repository.
     */
    private File resolveRepositoryFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("xmiFileName must not be empty");
        }

        File repoDir = new File(RepositoryScanner.REPO_PATH);
        File file = new File(repoDir, fileName);

        if (file.exists() && file.isFile()) {
        	return file;   
        }
        
        File found = findFileRecursively(repoDir, fileName);
        if (found != null) {
            return found;
        }
        
        throw new IllegalArgumentException("SysML XMI file not found in repository  or its subdirectories: " + file.getAbsolutePath());
    }
    
    
    /**
     * Recursively searches for a file with the given name starting from the specified directory.
     *
     * <p>This method explores the directory tree depth-first:
     * it first checks all files and subdirectories inside {@code dir}, and if a subdirectory
     * is encountered, it searches inside it recursively.</p>
     *
     * @param dir the root directory from which the search starts
     * @param targetFileName the exact name of the file to find
     * @return the {@link File} if found; otherwise {@code null}
     */
    private File findFileRecursively(File dir, String targetFileName) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFileRecursively(file, targetFileName);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().equals(targetFileName)) {
                return file;
            }
        }

        return null;
    }
    
    /**
     * Remove file extension.
     * Example: Example.xmi -> Example
     */
    private String baseName(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }
    
    
    
    
}
