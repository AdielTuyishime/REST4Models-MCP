package org.gst.REST4Models10.service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.egl.EglModule;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.eol.models.DefaultRelativePathResolver;
import org.gst.REST4Models10.repository.RepositoryScanner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ModelAnalysis {

    public Map<String, Object> analyzeSysmlComplexity(String xmiFileName) throws Exception {
        File sourceFile = resolveRepositoryFile(xmiFileName);

        File sysmlEcore = new ClassPathResource("repository/SysML.ecore").getFile();
        File eglFile = new ClassPathResource("epsilon/model_complexity_template.egl").getFile();

        EglModule module = new EglModule();
        module.parse(eglFile);

        EmfModel sourceModel = new EmfModel();
        StringProperties sourceProps = new StringProperties();
        sourceProps.put(EmfModel.PROPERTY_NAME, "SysML");
        sourceProps.put(EmfModel.PROPERTY_FILE_BASED_METAMODEL_URI, sysmlEcore.toURI().toString());
        sourceProps.put(EmfModel.PROPERTY_MODEL_URI, sourceFile.toURI().toString());
        sourceProps.put(EmfModel.PROPERTY_READONLOAD, "true");
        sourceProps.put(EmfModel.PROPERTY_STOREONDISPOSAL, "false");

        try {
            sourceModel.load(sourceProps, new DefaultRelativePathResolver());
            module.getContext().getModelRepository().addModel(sourceModel);

            Object result = module.execute();
            String analysisOutput = result != null ? result.toString() : "{}";

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "SysML complexity analysis executed successfully");
            response.put("sourceFile", sourceFile.getName());
            response.put("analysisOutput", analysisOutput);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> metrics = mapper.readValue(
                    analysisOutput,
                    new TypeReference<Map<String, Object>>() {}
            );
            response.putAll(metrics);

            return response;
        } finally {
            try {
                sourceModel.dispose();
            } catch (Exception ignored) {
            }
        }
    }

    private File resolveRepositoryFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("xmiFileName must not be empty");
        }

        File repoDir = new File(RepositoryScanner.REPO_PATH);
        File file = new File(repoDir, fileName);

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("SysML XMI file not found in repository: " + file.getAbsolutePath());
        }

        return file;
    }
}