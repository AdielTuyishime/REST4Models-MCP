package org.gst.REST4Models10.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.egl.EglModule;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.eol.models.DefaultRelativePathResolver;
import org.gst.REST4Models10.repository.RepositoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class M2TTransformationService {

    private static final Logger log = LoggerFactory.getLogger(M2TTransformationService.class);

    /**
     * nsURI for the SysML metamodel, as defined in SysML.ecore.
     * Used for registry-based lookup instead of file-based loading,
     * which avoids broken cross-references when running from a fat JAR.
     *
     * @see <a href="https://github.com/AdielTuyishime/REST4Models/issues/4">Issue #4</a>
     */
    private static final String SYSML_NS_URI = "https://www.omg.org/spec/SysML/20250201";
    private static final String TYPES_NS_URI = "https://www.omg.org/spec/UML/20161101/PrimitiveTypes";
    private static final String JSON_NS_URI  = "http://www.bamol.pt/json/Json";

    /**
     * All Epsilon script files that must be co-located in the same directory
     * for relative imports (e.g. {@code import "helpers.eol"}) to resolve.
     * When running from a fat JAR, these are extracted together into a
     * shared temporary directory.
     */
    private static final String[] EPSILON_FILES = {
            "epsilon/sysml_model_to_json_model.etl",
            "epsilon/m2t_sysml_to_json_text_helpers.eol",
            "epsilon/m2t_sysml_to_json_text_template.egl"
            
    };

    /**
     * Lazily-initialized shared temp directory for Epsilon scripts.
     * Created once on first use; all files extracted together so that
     * relative imports between EGL and EOL files work correctly.
     */
    private volatile File epsilonTempDir;

    /**
     * Execute the EOL transformation: M2T Transformation
     * SysML (.xmi) -> JSON (text)
     *
     * The generated target model is saved in repository/epsilon.
     */

    public String exportSysmlXmiToJson(String xmiFileName) throws Exception {
        ensureMetamodelsRegistered();

        File xmiFile = resolveRepositoryFile(xmiFileName);
        File eglFile = resolveEpsilonFile("m2t_sysml_to_json_text_template.egl");

        EglModule module = new EglModule();
        module.parse(eglFile);

        EmfModel model = new EmfModel();
        StringProperties properties = new StringProperties();

        properties.put(EmfModel.PROPERTY_NAME, "SysML");
        properties.put(EmfModel.PROPERTY_METAMODEL_URI, SYSML_NS_URI);
        properties.put(EmfModel.PROPERTY_MODEL_URI, xmiFile.toURI().toString());
        properties.put(EmfModel.PROPERTY_READONLOAD, "true");
        properties.put(EmfModel.PROPERTY_STOREONDISPOSAL, "false");

        try {
            model.load(properties, new DefaultRelativePathResolver());
            module.getContext().getModelRepository().addModel(model);

            Object result = module.execute();
            return result != null ? result.toString() : "";
        } finally {
            try {
                model.dispose();
            } catch (Exception ignored) {
            }
        }
    }

    public Map<String, Object> exportSysmlXmiToJsonAndSave(String xmiFileName, String outputFileName) throws Exception {
        File xmiFile = resolveRepositoryFile(xmiFileName);

        String outName = (outputFileName == null || outputFileName.isBlank())
                ? baseName(xmiFileName) + "_sysml.json"
                : outputFileName;

        if (!outName.toLowerCase().endsWith(".json")) {
            outName = outName + ".json";
        }

        File repoDir = new File(RepositoryScanner.REPO_PATH);
        //save the generated file into "/repository/epsilon"
        File epsilonDir = new File(repoDir, "epsilon");
        if (!epsilonDir.exists()) {
            epsilonDir.mkdirs();
        }

        File outputFile = new File(epsilonDir, outName);

        String json = exportSysmlXmiToJson(xmiFileName);
        Files.writeString(outputFile.toPath(), json, StandardCharsets.UTF_8);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "SysML XMI exported to JSON successfully");
        response.put("sourceFile", xmiFile.getName());
        response.put("outputFile", outputFile.getName());
        response.put("outputPath", outputFile.getAbsolutePath());

        return response;
    }



    /**
     * Verify that the SysML and PrimitiveTypes metamodels are registered
     * in the global EPackage registry. These are normally loaded by
     * RepositoryScanner at startup. If missing, the transformation would
     * fail with a null EPackage on cross-referenced EDataTypes.
     *
     * @throws IllegalStateException if required metamodels are not registered
     * @see <a href="https://github.com/AdielTuyishime/REST4Models/issues/4">Issue #4</a>
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
     * Resolve an Epsilon script file by name (e.g. "export-json.egl").
     *
     * <p>First tries direct classpath file access (works from IDE and
     * exploded JAR). If that fails (fat JAR), extracts <em>all</em>
     * Epsilon scripts listed in {@link #EPSILON_FILES} into a shared
     * temporary directory so that relative imports between EGL/EOL files
     * (e.g. {@code import "helpers.eol"}) resolve correctly.</p>
     *
     * @param fileName the script file name (without directory prefix)
     * @return a File pointing to the script on the real filesystem
     * @throws IOException if the resource cannot be read
     * @see <a href="https://github.com/AdielTuyishime/REST4Models/issues/1">Issue #1</a>
     */
    private File resolveEpsilonFile(String fileName) throws IOException {
        // Try direct classpath access first (IDE / exploded JAR)
        ClassPathResource resource = new ClassPathResource("epsilon/" + fileName);
        try {
            return resource.getFile();
        } catch (java.io.FileNotFoundException e) {
            // Fat JAR — need temp directory extraction
            return new File(getEpsilonTempDir(), fileName);
        }
    }

    /**
     * Lazily extract all Epsilon scripts into a shared temporary directory.
     * Thread-safe via double-checked locking on the volatile field.
     */
    private File getEpsilonTempDir() throws IOException {
        if (epsilonTempDir != null && epsilonTempDir.isDirectory()) {
            return epsilonTempDir;
        }

        synchronized (this) {
            if (epsilonTempDir != null && epsilonTempDir.isDirectory()) {
                return epsilonTempDir;
            }

            File tempDir = Files.createTempDirectory("r4m-epsilon-").toFile();
            tempDir.deleteOnExit();
            log.info("Extracting Epsilon scripts to temp directory: {}", tempDir.getAbsolutePath());

            for (String cpPath : EPSILON_FILES) {
                ClassPathResource res = new ClassPathResource(cpPath);
                String name = cpPath.substring(cpPath.lastIndexOf('/') + 1);
                File target = new File(tempDir, name);
                target.deleteOnExit();

                try (InputStream in = res.getInputStream()) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.debug("  Extracted: {} -> {}", cpPath, target.getAbsolutePath());
                }
            }

            epsilonTempDir = tempDir;
            return tempDir;
        }
    }

    /**
     * Resolve an input xmi file from the repository folder.
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
     * Remove the file extension from a file name.
     * Example:
     * Example.xmi -> Example
     */
    private String baseName(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    

   
    /**
     * Reads an JSON XMI model from the repository, transforms it into
     *  (.json) file, and saves the generated file into repository/epsilon.
     */
    
    
    
    

}

