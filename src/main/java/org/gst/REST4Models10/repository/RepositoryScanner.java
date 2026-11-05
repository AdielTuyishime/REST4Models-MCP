package org.gst.REST4Models10.repository;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RepositoryScanner {

    /**
     * Root directory of the model repository.
     * If the REPOSITORY_DIR environment variable is set, it is used.
     * Otherwise, the default local repository path is used.
     */
    public static final String REPO_PATH =
            System.getenv().getOrDefault("REPOSITORY_DIR", "src/main/resources/repository");

    private static final Logger log = LoggerFactory.getLogger(RepositoryScanner.class);

    /**
     * Maps each metamodel nsURI to its registered EPackage.
     */
    public static final Map<String, EPackage> nsUriToEPackage = new HashMap<>();

    /**
     * Maps each metamodel nsURI to the list of XMI model files conforming to it.
     */
    public static final Map<String, List<File>> nsUriToXmiFiles = new HashMap<>();

    /**
     * Shared ResourceSet used to load metamodels and models during repository scanning.
     */
    public static final ResourceSet resourceSet = new ResourceSetImpl();

    /**
     * Initializes the scanner once the Spring component is created.
     * Verifies the repository path, configures EMF factories, clears previous state,
     * and performs the initial scan.
     */
    @PostConstruct
    public void init() {
        File repoDir = new File(REPO_PATH);
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            throw new IllegalStateException("Repository directory not found: " + repoDir.getAbsolutePath());
        }

        log.info("Repository path: {}", repoDir.getAbsolutePath());

        configureFactories();
        clearState();
        rescanRepository();
    }

    /**
     * Re-scans the repository from scratch.
     * This clears all previously registered mappings and reloads the repository content.
     */
    public void rescanRepository() {
        File repoDir = new File(REPO_PATH);
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            throw new IllegalStateException("Repository directory not found: " + repoDir.getAbsolutePath());
        }

        clearState();
        resourceSet.getResources().clear();
        configureFactories();
        scanRepository(repoDir);
    }

    /**
     * Scans the repository in two phases:
     * 1. Load and register all metamodels (.ecore files)
     * 2. Load all model instances (.xmi files) and associate them with their nsURI
     *
     * This two-step approach ensures that all required metamodels are registered
     * before any model instance is loaded.
     *
     * @param repoDir the root repository directory to scan
     */
    public void scanRepository(File repoDir) {
        log.info("Scanning repository: {}", repoDir.getAbsolutePath());

        List<File> ecoreFiles = new ArrayList<>();
        List<File> xmiFiles = new ArrayList<>();

        collectFiles(repoDir, ecoreFiles, xmiFiles);

        // Step 1: register all metamodels first
        for (File ecoreFile : ecoreFiles) {
            List<EPackage> pkgs = loadAndRegisterEcore(ecoreFile.getAbsolutePath());

            for (EPackage pkg : pkgs) {
                log.info("✅ Registered metamodel: {} ({})", pkg.getName(), pkg.getNsURI());
                nsUriToEPackage.put(pkg.getNsURI(), pkg);
                nsUriToXmiFiles.putIfAbsent(pkg.getNsURI(), new ArrayList<>());
                resourceSet.getPackageRegistry().put(pkg.getNsURI(), pkg);
            }
        }

        // Step 2: load all XMI models after metamodel registration
        for (File xmiFile : xmiFiles) {
            log.info("📄 Found XMI model: {}", xmiFile.getName());

            try {
                Resource xmiResource = loadResource(xmiFile);

                for (EObject root : xmiResource.getContents()) {
                    String nsUri = root.eClass().getEPackage().getNsURI();

                    if (nsUriToXmiFiles.containsKey(nsUri)) {
                        nsUriToXmiFiles.get(nsUri).add(xmiFile);
                        log.info("🔗 Associated {} with nsURI: {}", xmiFile.getAbsolutePath(), nsUri);
                    } else {
                        log.info("Unrecognized nsURI in {}: {}", xmiFile.getAbsolutePath(), nsUri);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load XMI file {}: {}", xmiFile.getAbsolutePath(), e.getMessage());
            }
        }

        logSummary();
    }

    /**
     * Recursively collects all .ecore and .xmi files from the given directory.
     *
     * Generated output folders such as 'epsilon' are skipped to prevent generated
     * transformation outputs from being treated as repository input models.
     *
     * @param dir the directory to inspect
     * @param ecoreFiles collection where discovered .ecore files are added
     * @param xmiFiles collection where discovered .xmi files are added
     */
    private void collectFiles(File dir, List<File> ecoreFiles, List<File> xmiFiles) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Skip generated transformation outputs
                if ("epsilon".equalsIgnoreCase(file.getName())) {
                    log.info("Skipping generated directory: {}", file.getAbsolutePath());
                    continue;
                }
                collectFiles(file, ecoreFiles, xmiFiles);
            } else if (file.getName().toLowerCase().endsWith(".ecore")) {
                ecoreFiles.add(file);
            } else if (file.getName().toLowerCase().endsWith(".xmi")) {
                xmiFiles.add(file);
            }
        }
    }

    /**
     * Returns the map of registered EPackages by nsURI.
     *
     * @return a map from nsURI to EPackage
     */
    public Map<String, EPackage> getRegisteredEPackages() {
        return nsUriToEPackage;
    }

    /**
     * Returns the map of XMI model files grouped by their metamodel nsURI.
     *
     * @return a map from nsURI to list of XMI files
     */
    public Map<String, List<File>> getModelFilesMap() {
        return nsUriToXmiFiles;
    }

    /**
     * Loads an .ecore file and registers all root packages and subpackages found in it.
     *
     * @param ecorePath absolute path to the .ecore file
     * @return the list of registered EPackages
     */
    public static List<EPackage> loadAndRegisterEcore(String ecorePath) {
        configureFactories();

        URI uri = URI.createFileURI(new File(ecorePath).getAbsolutePath());
        Resource res = resourceSet.getResource(uri, false);
        if (res == null) {
            res = resourceSet.createResource(uri);
        }

        try {
            if (!res.isLoaded()) {
                res.load(null);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Ecore file: " + ecorePath, e);
        }

        List<EPackage> registered = new ArrayList<>();
        for (EObject obj : res.getContents()) {
            if (obj instanceof EPackage pkg) {
                registerRecursively(pkg, registered);
            }
        }

        return registered;
    }

    /**
     * Registers the given EPackage and all of its subpackages recursively
     * in both the global EMF registry and the shared ResourceSet registry.
     *
     * @param pkg the package to register
     * @param registered list collecting all successfully registered packages
     */
    public static void registerRecursively(EPackage pkg, List<EPackage> registered) {
        if (pkg == null || pkg.getNsURI() == null || pkg.getNsURI().isBlank()) {
            return;
        }

        EPackage.Registry.INSTANCE.put(pkg.getNsURI(), pkg);
        resourceSet.getPackageRegistry().put(pkg.getNsURI(), pkg);

        if (!registered.contains(pkg)) {
            registered.add(pkg);
        }

        for (EPackage sub : pkg.getESubpackages()) {
            registerRecursively(sub, registered);
        }
    }

    /**
     * Configures the EMF resource factories needed to load .ecore and .xmi files.
     */
    private static void configureFactories() {
        Map<String, Object> map = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();
        map.put("ecore", new EcoreResourceFactoryImpl());
        map.put("xmi", new XMIResourceFactoryImpl());
        map.put("*", new XMIResourceFactoryImpl());
    }

    /**
     * Loads a model resource from file using the shared ResourceSet.
     *
     * @param file the XMI file to load
     * @return the loaded EMF resource
     * @throws Exception if the resource cannot be loaded
     */
    private Resource loadResource(File file) throws Exception {
        URI uri = URI.createFileURI(file.getAbsolutePath());
        Resource resource = resourceSet.getResource(uri, false);

        if (resource == null) {
            resource = resourceSet.createResource(uri);
        }

        if (!resource.isLoaded()) {
            resource.load(null);
        }

        return resource;
    }

    /**
     * Clears all repository scanner caches.
     */
    private void clearState() {
        nsUriToEPackage.clear();
        nsUriToXmiFiles.clear();
    }

    /**
     * Logs a summary of the discovered model files grouped by nsURI.
     */
    private void logSummary() {
        log.info("Summary:");
        nsUriToXmiFiles.forEach((nsUri, filesList) -> {
            log.info(" - {}:", nsUri);
            for (File f : filesList) {
                log.info("    • {}", f.getAbsolutePath());
            }
        });
    }

    /**
     * Creates a new ResourceSet configured with XMI/Ecore factories and all
     * registered EPackages currently known by the repository scanner.
     *
     * This is useful when another service needs an isolated ResourceSet that
     * can still resolve repository metamodels correctly.
     *
     * @return a configured ResourceSet
     */
    public static ResourceSet createConfiguredResourceSet() {
        ResourceSet rs = new ResourceSetImpl();

        Map<String, Object> map = rs.getResourceFactoryRegistry().getExtensionToFactoryMap();
        map.put("ecore", new EcoreResourceFactoryImpl());
        map.put("xmi", new XMIResourceFactoryImpl());
        map.put("*", new XMIResourceFactoryImpl());

        for (Map.Entry<String, EPackage> entry : nsUriToEPackage.entrySet()) {
            rs.getPackageRegistry().put(entry.getKey(), entry.getValue());
        }

        return rs;
    }
}