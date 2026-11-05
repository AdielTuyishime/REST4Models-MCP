package org.gst.REST4Models10.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.gst.REST4Models10.repository.RepositoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Service
public class Services {
	
	private static final Logger log = LoggerFactory.getLogger(RepositoryScanner.class);

	/*--------------Metamodel-level----------------------------------
	 *--------------------------------------------------------------- 
	 */

	/**---------------------------------------------
	 * List all loaded EPackages from the repository
	 * --------------------------------------------- 
	 * We can simply expose the already-populated map RepositoryScanner.nsUriToEPackage
	 * This returns a list of simple maps with name, prefix, and URI of each package.
	 */

	public List<Map<String, String>> getAllRegisteredEPackages() {
		List<Map<String, String>> packages = new ArrayList<>();

		for (EPackage pkg : RepositoryScanner.nsUriToEPackage.values()) {
			Map<String, String> info = new HashMap<>();
			info.put("name", pkg.getName());
			info.put("nsPrefix", pkg.getNsPrefix());
			info.put("nsURI", pkg.getNsURI());
			packages.add(info);
		}

		return packages;
	}


	/**----------------------------------------
	 * Fetch classes from a specific package
	 * ----------------------------------------
	 */
	public List<EClass> getAllEClassesFromPackage(String packageName) {
		for (EPackage ePackage : RepositoryScanner.nsUriToEPackage.values()) {
			if (ePackage.getName().equals(packageName)) {
				return collectAllEClasses(ePackage);
			}
		}
		return Collections.emptyList(); // Not found
	}

	private List<EClass> collectAllEClasses(EPackage ePackage) {
		List<EClass> result = new ArrayList<>();

		for (EClassifier classifier : ePackage.getEClassifiers()) {
			if (classifier instanceof EClass eClass) {
				result.add(eClass);
			}
		}

		// Recurse into subpackages
		for (EPackage subPackage : ePackage.getESubpackages()) {
			result.addAll(collectAllEClasses(subPackage));
		}

		return result;
	}


	/**-----------------------------------------------------
	 * Get Class details including attributes and references
	 * -----------------------------------------------------
	 * 
	 */

	public Map<String, Object> getEClassDetailsFromPackage(String packageName, String className) {
		for (EPackage ePackage : RepositoryScanner.nsUriToEPackage.values()) {
			EPackage matched = findPackageByName(ePackage, packageName);
			if (matched != null) {
				for (EClassifier classifier : matched.getEClassifiers()) {
					if (classifier instanceof EClass eClass && eClass.getName().equals(className)) {
						return getEClassDetails(eClass);  // Use helper method from before
					}
				}
			}
		}
		return Map.of("error", "Class not found in the given package.");
	}

	/**----------------------------------
	 * GET details of a specific Eclass 
	 * ----------------------------------
	 */
	private Map<String, Object> getEClassDetails(EClass eClass) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", eClass.getName());

		List<Map<String, String>> attributes = new ArrayList<>();
		for (EAttribute attr : eClass.getEAttributes()) {
			attributes.add(Map.of(
					"name", attr.getName(),
					"type", attr.getEType().getName()
					));
		}

		List<Map<String, String>> references = new ArrayList<>();
		for (EReference ref : eClass.getEReferences()) {
			references.add(Map.of(
					"name", ref.getName(),
					"type", ref.getEType().getName(),
					"cardinality", getCardinality(ref)
					));
		}

		details.put("attributes", attributes);
		details.put("references", references);
		return details;
	}

	private String getCardinality(EStructuralFeature feature) {
		int lower = feature.getLowerBound();
		int upper = feature.getUpperBound();
		return upper == -1 ? lower + "..*" : lower + ".." + upper;
	}


	private EPackage findPackageByName(EPackage root, String packageName) {
		if (root.getName().equals(packageName)) return root;
		for (EPackage sub : root.getESubpackages()) {
			EPackage result = findPackageByName(sub, packageName);
			if (result != null) return result;
		}
		return null;
	}


	/**---------------------------------------------- 
	 * Delete an attribute from an EClass
	 * ----------------------------------------------
	 */
	public boolean deleteAttributeFromEClass(
			String packageName,
			String className,
			String attributeName
			) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}

				if (targetClass == null) {
					log.info("EClass not found: " + className);
					return false;
				}

				EAttribute attrToRemove = null;
				for (EAttribute attr : targetClass.getEAttributes()) {
					if (attr.getName().equals(attributeName)) {
						attrToRemove = attr;
						break;
					}
				}

				if (attrToRemove == null) {
					log.info("Attribute not found: " + attributeName);
					return false;
				}

				boolean removed = targetClass.getEStructuralFeatures().remove(attrToRemove);

				if (removed) {
					Resource res = matchedPackage.eResource();
					if (res != null) {
						try {
							res.save(null);
							log.info("✅ Attribute deleted and .ecore saved.");
							return true;
						} catch (IOException e) {
							e.printStackTrace();
							return false;
						}
					} else {
						log.info("⚠️ Could not find resource for EPackage.");
						return false;
					}
				} else {
					log.info("⚠️ Could not remove attribute.");
					return false;
				}
			}
		}

		log.info("❌ Package not found: " + packageName);
		return false;
	}



	/**-----------------------------------------
	 * Renames an EAttribute in a given EClass.
	 *------------------------------------------
	 * Steps:
	 * 1) Locate the target EPackage and EClass.
	 * 2) Find the EAttribute by its current (old) name.
	 * 3) Update the attribute name to the provided new name.
	 * 4) Persist the updated .ecore model back to disk.
	 */
	public boolean renameAttributeInEClass(
			String packageName,
			String className,
			String oldAttributeName,
			String newAttributeName
			) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}

				if (targetClass == null) {
					log.info("❌ EClass not found: " + className);
					return false;
				}

				EAttribute attrToRename = null;
				for (EAttribute attr : targetClass.getEAttributes()) {
					if (attr.getName().equals(oldAttributeName)) {
						attrToRename = attr;
						break;
					}
				}

				if (attrToRename == null) {
					log.info("❌ Attribute not found: " + oldAttributeName);
					return false;
				}

				attrToRename.setName(newAttributeName);

				Resource res = matchedPackage.eResource();
				if (res != null) {
					try {
						res.save(null);
						log.info("✅ Attribute renamed and .ecore saved.");
						return true;
					} catch (IOException e) {
						e.printStackTrace();
						return false;
					}
				} else {
					log.info("⚠️ Could not find resource for EPackage.");
					return false;
				}
			}
		}

		log.info("❌ Package not found: " + packageName);
		return false;
	}


	/**----------------------------------------
	 * Adds a new EAttribute to a given EClass.
	 *-----------------------------------------
	 * Steps:
	 * 1) Locate the target {@link org.eclipse.emf.ecore.EPackage}.
	 * 2) Locate the target {@link org.eclipse.emf.ecore.EClass}.
	 * 3) Create a new {@link org.eclipse.emf.ecore.EAttribute}.
	 * 4) Set the attribute name and EType (e.g., {@code EcorePackage.Literals.ESTRING}).
	 * 5) Add the attribute to the EClass structural features.
	 * 6) Persist the updated .ecore model back to disk.
	 */
	public boolean addAttributeToEClass(
			String packageName,
			String className,
			String attributeName,
			String attributeTypeName
			) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}

				if (targetClass == null) {
					log.info("❌ EClass not found: " + className);
					return false;
				}

				// Check if attribute already exists
				for (EAttribute attr : targetClass.getEAttributes()) {
					if (attr.getName().equals(attributeName)) {
						log.info("⚠️ Attribute already exists: " + attributeName);
						return false;
					}
				}

				// Find the type (from Ecore)
				EDataType type = (EDataType) EcorePackage.eINSTANCE.getEClassifier(attributeTypeName);
				if (type == null) {
					log.info("❌ Unknown Ecore type: " + attributeTypeName);
					return false;
				}

				// Create the new EAttribute
				EAttribute newAttr = EcoreFactory.eINSTANCE.createEAttribute();
				newAttr.setName(attributeName);
				newAttr.setEType(type);

				targetClass.getEStructuralFeatures().add(newAttr);

				Resource res = matchedPackage.eResource();
				if (res != null) {
					try {
						res.save(null);
						log.info("✅ Attribute added and .ecore saved.");
						return true;
					} catch (IOException e) {
						e.printStackTrace();
						return false;
					}
				} else {
					log.info("⚠️ Could not find resource for EPackage.");
					return false;
				}
			}
		}

		log.info("❌ Package not found: " + packageName);
		return false;
	}


	/**-------------------------------------------
	 * Creates a new EClass in a target EPackage.
	 *--------------------------------------------
	 * Steps:
	 * 1) Iterate over all {@code RepositoryScanner}-discovered packages.
	 * 2) Select the {@link org.eclipse.emf.ecore.EPackage} matching {@code packageName}.
	 * 3) Check whether an {@link org.eclipse.emf.ecore.EClass} with the same name already exists (safe-guard).
	 * 4) Create and name the new {@link org.eclipse.emf.ecore.EClass}.
	 * 5) Add the new class to the selected {@link org.eclipse.emf.ecore.EPackage}.
	 * 6) Save the updated .ecore file in place
	 */
	public boolean createEClass(
			String packageName,
			String newClassName
			) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				// Check if class already exists
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(newClassName)) {
						log.info("EClass already exists: " + newClassName);
						return false;
					}
				}

				// Create EClass
				EClass newEClass = EcoreFactory.eINSTANCE.createEClass();
				newEClass.setName(newClassName);

				matchedPackage.getEClassifiers().add(newEClass);

				Resource res = matchedPackage.eResource();
				if (res != null) {
					try {
						res.save(null);
						log.info("EClass created and .ecore saved.");
						return true;
					} catch (IOException e) {
						e.printStackTrace();
						return false;
					}
				} else {
					log.info("Could not find resource for EPackage.");
					return false;
				}
			}
		}

		log.info("Package not found: " + packageName);
		return false;
	}


	/**-----------------------------------------------------------
	 * Adds an EReference from a source EClass to a target EClass
	 *------------------------------------------------------------
	 * Steps:
	 * 1) Resolve both the source and target {@link org.eclipse.emf.ecore.EClass} instances.
	 * 2) Check whether an {@link org.eclipse.emf.ecore.EReference} with the same name already exists on the source class.
	 * 3) Create the reference and configure its:
	 *    - name
	 *    - EType (target EClass)
	 *    - containment flag
	 *    - multiplicity (lowerBound / upperBound)
	 * 4) Add the reference to the source class structural features.
	 * 5) Persist the updated .ecore model back to disk.
	 */
	public boolean addReferenceToEClass(
			String packageName,
			String sourceClassName,
			String targetClassName,
			String referenceName,
			boolean containment,
			boolean isMany
			) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass sourceEClass = null;
				EClass targetEClass = null;

				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec) {
						if (ec.getName().equals(sourceClassName)) {
							sourceEClass = ec;
						} else if (ec.getName().equals(targetClassName)) {
							targetEClass = ec;
						}
					}
				}

				if (sourceEClass == null) {
					log.info("❌ Source EClass not found: " + sourceClassName);
					return false;
				}

				if (targetEClass == null) {
					log.info("❌ Target EClass not found: " + targetClassName);
					return false;
				}

				// Check if reference already exists
				for (EReference ref : sourceEClass.getEReferences()) {
					if (ref.getName().equals(referenceName)) {
						log.info("⚠️ EReference already exists: " + referenceName);
						return false;
					}
				}

				EReference newRef = EcoreFactory.eINSTANCE.createEReference();
				newRef.setName(referenceName);
				newRef.setEType(targetEClass);
				newRef.setContainment(containment);
				newRef.setLowerBound(0);
				newRef.setUpperBound(isMany ? -1 : 1);

				sourceEClass.getEStructuralFeatures().add(newRef);

				Resource res = matchedPackage.eResource();
				if (res != null) {
					try {
						res.save(null);
						log.info("✅ EReference created and .ecore saved.");
						return true;
					} catch (IOException e) {
						e.printStackTrace();
						return false;
					}
				} else {
					log.info("⚠️ Could not find resource for EPackage.");
					return false;
				}
			}
		}

		log.info("❌ Package not found: " + packageName);
		return false;
	}


	/**----------------------------------------------------------
	 * Creates an EOpposite relationship between two EReferences
	 *-----------------------------------------------------------
	 *
	 * This establishes a bidirectional association so that navigating or updating one reference
	 * is reflected through its opposite counterpart.
	 *
	 * Behavior:
	 * - If both references exist: link them as opposites (if not already linked).
	 * - If only {@code refNameA} exists: create {@code refNameB}, then link both as opposites.
	 * - If neither reference exists: create both references, then link them as opposites.
	 */
	public boolean createOppositeReferences(
			String packageName,
			String sourceClassName,
			String targetClassName,
			String refNameA,
			String refNameB,
			boolean containment,
			boolean isMany
			) {
		for (EPackage rootPackage : RepositoryScanner.nsUriToEPackage.values()) {
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {
				EClass source = null;
				EClass target = null;

				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec) {
						if (ec.getName().equals(sourceClassName)) source = ec;
						if (ec.getName().equals(targetClassName)) target = ec;
					}
				}

				if (source == null || target == null) {
					log.info("❌ Could not find both EClasses.");
					return false;
				}

				// Look for existing references
				EReference existingRefA = source.getEStructuralFeatures().stream()
						.filter(f -> f instanceof EReference && f.getName().equals(refNameA))
						.map(f -> (EReference) f)
						.findFirst()
						.orElse(null);

				EReference existingRefB = target.getEStructuralFeatures().stream()
						.filter(f -> f instanceof EReference && f.getName().equals(refNameB))
						.map(f -> (EReference) f)
						.findFirst()
						.orElse(null);

				// Create missing reference(s)
				if (existingRefA == null) {
					existingRefA = EcoreFactory.eINSTANCE.createEReference();
					existingRefA.setName(refNameA);
					existingRefA.setEType(target);
					existingRefA.setContainment(containment);
					existingRefA.setLowerBound(0);
					existingRefA.setUpperBound(isMany ? -1 : 1);
					source.getEStructuralFeatures().add(existingRefA);
					log.info("➕ Created reference " + refNameA + " in class " + sourceClassName);
				}

				if (existingRefB == null) {
					existingRefB = EcoreFactory.eINSTANCE.createEReference();
					existingRefB.setName(refNameB);
					existingRefB.setEType(source);
					existingRefB.setContainment(false); // usually not needed for reverse
					existingRefB.setLowerBound(0);
					existingRefB.setUpperBound(1);
					target.getEStructuralFeatures().add(existingRefB);
					log.info("➕ Created reference " + refNameB + " in class " + targetClassName);
				}

				// Ensure both are linked as eOpposites
				existingRefA.setEOpposite(existingRefB);
				existingRefB.setEOpposite(existingRefA);

				try {
					matchedPackage.eResource().save(null);
					log.info("✅ Updated metamodel saved.");
					return true;
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
		}

		log.info("❌ Package not found.");
		return false;
	}



	/**-------------------------------------------------------------
	 * Deletes an EReference} from a given EClass within an EPackage
	 *--------------------------------------------------------------
	 *
	 * @param packageName    name of the EPackage containing the class
	 * @param className      name of the EClass from which the reference will be removed
	 * @param referenceName  name of the EReference to delete
	 * @return {@code true} if the reference was found and removed; {@code false} otherwise
	 */

	/**
	 * Deletion logic (including opposites):
	 * 1) Locate the EReference on {@code className} by {@code referenceName}.
	 * 2) If the reference has an opposite ({@code ref.getEOpposite()}):
	 *    - resolve the opposite reference's owning EClass
	 *    - remove the opposite reference from that class as well
	 * 3) Remove the original reference from the source class.
	 * 4) Persist the updated .ecore model back to disk.
	 */

	public boolean deleteReference(String packageName, String className, String referenceName) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);

			if (matchedPackage != null) {
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass eClass && eClass.getName().equals(className)) {

						// Find reference to delete
						EReference toRemove = null;
						for (EReference ref : eClass.getEReferences()) {
							if (ref.getName().equals(referenceName)) {
								toRemove = ref;
								break;
							}
						}

						if (toRemove != null) {
							// 🔁 Delete opposite reference if exists
							EReference opposite = toRemove.getEOpposite();
							if (opposite != null) {
								EClass oppositeClass = opposite.getEContainingClass();
								if (oppositeClass.getEStructuralFeatures().contains(opposite)) {
									oppositeClass.getEStructuralFeatures().remove(opposite);
									log.info("🔁 Opposite reference removed: " + opposite.getName());
								}
							}

							// ✅ Remove the reference
							eClass.getEStructuralFeatures().remove(toRemove);
							log.info("✅ Reference removed: " + referenceName);

							// Save the changes to .ecore file
							Resource res = matchedPackage.eResource();
							if (res != null) {
								try {
									res.save(null);
									return true;
								} catch (IOException e) {
									e.printStackTrace();
									return false;
								}
							} else {
								log.info("⚠️ Could not locate .ecore resource to save.");
								return false;
							}

						} else {
							log.info("⚠️ Reference not found: " + referenceName);
							return false;
						}
					}
				}
			}
		}

		log.info("❌ Package or class not found.");
		return false;
	}


	/**------------------------
	 * Creates a new EPackage
	 *-------------------------
	 * A package must define:
	 * - {@code name}    : the package name
	 * - {@code nsPrefix}: the namespace prefix (short alias)
	 * - {@code nsURI}   : the unique namespace URI identifying the package
	 */
	public EPackage createAndRegisterEPackage(String name, String nsPrefix, String nsURI) {
		EcoreFactory factory = EcoreFactory.eINSTANCE;
		EPackage newPkg = factory.createEPackage();

		newPkg.setName(name);
		newPkg.setNsPrefix(nsPrefix);
		newPkg.setNsURI(nsURI);

		// Reuse your existing method to register
		List<EPackage> registered = new ArrayList<>();
		RepositoryScanner.registerRecursively(newPkg, registered);  // reuse the recursive method

		// Also add to your RepoScanner maps if needed
		RepositoryScanner.nsUriToEPackage.put(nsURI, newPkg);
		RepositoryScanner.nsUriToXmiFiles.put(nsURI, new ArrayList<>());

		log.info("✅ EPackage created and registered: " + nsURI);
		return newPkg;
	}



	/**----------------------------------------------
	 * Save EPackage to .ecore File and create file name with an extension .ecore
	 * e.g: PackageName.ecore
	 */
	public void saveEPackageToFile(EPackage ePackage, String directoryPath) throws IOException {
		ResourceSet resourceSet = new ResourceSetImpl();

		// ✅ Ensure factories are registered
		Resource.Factory.Registry registry = Resource.Factory.Registry.INSTANCE;
		Map<String, Object> extToFactory = registry.getExtensionToFactoryMap();
		extToFactory.put("ecore", new EcoreResourceFactoryImpl());

		// ✅ Build file path from package name
		String fileName = ePackage.getName() + ".ecore";
		File directory = new File(directoryPath);
		if (!directory.exists()) {
			directory.mkdirs(); // create directories if not exist
		}
		File file = new File(directory, fileName);
		URI uri = URI.createFileURI(file.getAbsolutePath());

		// ✅ Create and save resource
		Resource resource = resourceSet.createResource(uri);
		if (resource == null) {
			throw new IOException("Could not create resource for URI: " + uri);
		}

		resource.getContents().add(ePackage);
		resource.save(null);

		log.info("EPackage saved to file: " + file.getAbsolutePath());
	}


	/**-------------------------------------------------------
	 * Retrieves the metamodel structure for a given EPackage
	 *--------------------------------------------------------
	 * Given an EPackage name, returns a structured representation of:
	 * - all EClasses in the package
	 * - for each class: its EAttributes and EReferences
	 */

	public List<Map<String, Object>> getMetamodelStructure(String packageName) {
		List<Map<String, Object>> classDetailsList = new ArrayList<>();

		for (EPackage pkg : RepositoryScanner.nsUriToEPackage.values()) {
			EPackage matched = findPackageByName(pkg, packageName);
			if (matched != null) {
				for (EClassifier classifier : matched.getEClassifiers()) {
					if (classifier instanceof EClass eClass) {
						Map<String, Object> classMap = new LinkedHashMap<>();
						classMap.put("className", eClass.getName());

						// ➕ Attributes with multiplicity
						List<Map<String, Object>> attributes = new ArrayList<>();
						for (EAttribute attr : eClass.getEAllAttributes()) {
							attributes.add(Map.of(
									"name", attr.getName(),
									"type", attr.getEAttributeType().getName(),
									"multiplicity", formatMultiplicity(attr.getLowerBound(), attr.getUpperBound())
									));
						}
						classMap.put("attributes", attributes);

						// ➕ References with multiplicity
						List<Map<String, Object>> references = new ArrayList<>();
						for (EReference ref : eClass.getEAllReferences()) {
							Map<String, Object> refMap = new LinkedHashMap<>();
							refMap.put("name", ref.getName());
							refMap.put("type", ref.getEType().getName());
							refMap.put("containment", ref.isContainment());
							refMap.put("multiplicity", formatMultiplicity(ref.getLowerBound(), ref.getUpperBound()));
							if (ref.getEOpposite() != null) {
								refMap.put("opposite", ref.getEOpposite().getName());
							}
							references.add(refMap);
						}
						classMap.put("references", references);

						classDetailsList.add(classMap);
					}
				}
				return classDetailsList;
			}
		}

		return List.of();
	}

	private String formatMultiplicity(int lower, int upper) {
		String upperStr = (upper == -1) ? "*" : String.valueOf(upper);
		return lower + ".." + upperStr;
	}



	/*--------------------With Enumeration---------------
	 * Given a package, get class, attributes, references, and Enumerations
	 */
	public Map<String, Object> getMetamodelStructureEnumeration(String packageName) {
		Map<String, Object> result = new LinkedHashMap<>();

		List<Map<String, Object>> classDetailsList = new ArrayList<>();
		List<Map<String, Object>> enumDetailsList = new ArrayList<>();

		for (EPackage pkg : RepositoryScanner.nsUriToEPackage.values()) {
			EPackage matched = findPackageByName(pkg, packageName);
			if (matched != null) {
				for (EClassifier classifier : matched.getEClassifiers()) {

					// EClass: Add class info with attributes and references
					if (classifier instanceof EClass eClass) {
						Map<String, Object> classMap = new LinkedHashMap<>();
						classMap.put("className", eClass.getName());

						// Attributes with multiplicity
						List<Map<String, Object>> attributes = new ArrayList<>();
						for (EAttribute attr : eClass.getEAllAttributes()) {
							attributes.add(Map.of(
									"name", attr.getName(),
									"type", attr.getEAttributeType().getName(),
									"multiplicity", formatMultiplicity(attr.getLowerBound(), attr.getUpperBound())
									));
						}
						classMap.put("attributes", attributes);

						// References with multiplicity and containment
						List<Map<String, Object>> references = new ArrayList<>();
						for (EReference ref : eClass.getEAllReferences()) {
							Map<String, Object> refMap = new LinkedHashMap<>();
							refMap.put("name", ref.getName());
							refMap.put("type", ref.getEType().getName());
							refMap.put("containment", ref.isContainment());
							refMap.put("multiplicity", formatMultiplicity(ref.getLowerBound(), ref.getUpperBound()));
							if (ref.getEOpposite() != null) {
								refMap.put("opposite", ref.getEOpposite().getName());
							}
							references.add(refMap);
						}
						classMap.put("references", references);

						classDetailsList.add(classMap);
					}

					// EEnum: Add enum info with its literals
					else if (classifier instanceof EEnum eEnum) {
						Map<String, Object> enumMap = new LinkedHashMap<>();
						enumMap.put("enumName", eEnum.getName());

						/*
	                    List<String> literals = eEnum.getELiterals().stream()
	                        .map(EEnumLiteral::getName)
	                        .toList();
						 */
						// Include both literal name and value
						List<Map<String, Object>> literals = new ArrayList<>();
						for (EEnumLiteral lit : eEnum.getELiterals()) {
							Map<String, Object> literalMap = new LinkedHashMap<>();
							literalMap.put("name", lit.getName());
							literalMap.put("value", lit.getValue());
							literalMap.put("literal", lit.getLiteral()); // Optional
							literals.add(literalMap);
						}
						enumMap.put("literals", literals);
						enumDetailsList.add(enumMap);
					}
				}

				// Package matched → return result
				result.put("classes", classDetailsList);
				result.put("enumerations", enumDetailsList);
				return result;
			}
		}

		// Package not found
		return Map.of("error", "Package not found: " + packageName);
	}



	/**----------------------------------------------------------------------
	 * Summary Statistics: Calculates and returns the total number of EClasses,
	 * EAttributes, and EReferences
	 * contained in a specified EPackage (looked up by package name).
	 * ----------------------------------------------------------------------
	 */

	public Map<String, Integer> countElementsInPackage(String packageName) {
		Map<String, Integer> result = new HashMap<>();
		int classCount = 0;
		int attributeCount = 0;
		int referenceCount = 0;

		for (EPackage root : RepositoryScanner.nsUriToEPackage.values()) {
			EPackage matched = findPackageByName(root, packageName);
			if (matched != null) {
				for (EClassifier classifier : matched.getEClassifiers()) {
					if (classifier instanceof EClass eClass) {
						classCount++;
						attributeCount += eClass.getEAttributes().size();
						referenceCount += eClass.getEReferences().size();
					}
				}

				result.put("classes", classCount);
				result.put("attributes", attributeCount);
				result.put("references", referenceCount);
				return result;
			}
		}

		log.info("❌ Package not found: " + packageName);
		return Map.of(
				"classes", 0,
				"attributes", 0,
				"references", 0
				);
	}


	/**
	 * Deletes an EClass from an EPackage.
	 *
	 * This operation:
	 * - Scans all .ecore files under {@code RepoScanner.REPO_PATH}
	 * - Locates the target EPackage
	 * - Removes the target EClass from the package classifiers
	 * - Saves the updated .ecore resource back to disk (e.g., {@code resource.save(null)})
	 */

	public boolean deleteEClass(String packageName, String className) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);

			if (matchedPackage != null) {
				EClassifier toRemove = null;

				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass eClass && eClass.getName().equals(className)) {
						toRemove = classifier;
						break;
					}
				}

				if (toRemove != null) {
					EClass toRemoveClass = (EClass) toRemove;

					// === Issue #3 fix: clean up dangling references before removal ===
					// Without this cleanup, removing an EClass that is referenced by
					// other classes (via EReference eType or eSuperTypes) corrupts the
					// in-memory EMF model and causes sibling classes to disappear
					// during serialization.

					// 1. Clear outgoing opposite links from the class being deleted
					for (EReference ref : new ArrayList<>(toRemoveClass.getEReferences())) {
						EReference opposite = ref.getEOpposite();
						if (opposite != null) {
							opposite.setEOpposite(null);
							ref.setEOpposite(null);
							log.info("Cleared opposite link: " + className + "."
									+ ref.getName() + " <-> " + opposite.getEContainingClass().getName()
									+ "." + opposite.getName());
						}
					}

					// 2. Remove references in other classes that target this class
					for (EClassifier other : new ArrayList<>(matchedPackage.getEClassifiers())) {
						if (other instanceof EClass otherClass && otherClass != toRemoveClass) {
							List<EReference> danglingRefs = new ArrayList<>();
							for (EReference ref : otherClass.getEReferences()) {
								if (ref.getEReferenceType() == toRemoveClass) {
									danglingRefs.add(ref);
								}
							}
							for (EReference ref : danglingRefs) {
								if (ref.getEOpposite() != null) {
									ref.setEOpposite(null);
								}
								otherClass.getEStructuralFeatures().remove(ref);
								log.info("Removed dangling reference: " + otherClass.getName()
										+ "." + ref.getName() + " -> " + className);
							}

							// 3. Remove from eSuperTypes if the deleted class was a supertype
							if (otherClass.getESuperTypes().remove(toRemoveClass)) {
								log.info("Removed supertype: " + otherClass.getName()
										+ " -extends-> " + className);
							}
						}
					}

					matchedPackage.getEClassifiers().remove(toRemove);
					Resource res = matchedPackage.eResource();

					if (res != null) {
						try {
							res.save(null);
							log.info("✅ EClass deleted and .ecore saved: " + className);
							return true;
						} catch (IOException e) {
							e.printStackTrace();
							return false;
						}
					} else {
						log.info("⚠️ Could not find resource for EPackage.");
						return false;
					}
				} else {
					log.info("❌ Class not found: " + className);
					return false;
				}
			}
		}

		log.info("❌ Package not found: " + packageName);
		return false;
	}



	/**---------------------Model-Level ------------------------------------------------
	 * 						Dealing with Model Instances
	 * ---------------------------------------------------------------------------------
	 */

	
	/**
	 * Retrieves all instances of a specific EClass
	 * from the model(s) associated with a given metamodel.
	 */
	public List<EObject> getInstancesOfClass(String packageName, String className) {
		List<EObject> result = new ArrayList<>();

		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}

				if (targetClass == null) {
					log.info("❌ Class not found in package.");
					return List.of();
				}

				String nsUri = matchedPackage.getNsURI();
				List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

				for (File xmiFile : xmiFiles) {
					Resource resource = RepositoryScanner.resourceSet.getResource(
							URI.createFileURI(xmiFile.getAbsolutePath()), true
							);

					for (EObject obj : resource.getContents()) {
						// ✅ Pass the same result collector
						findInstancesOfEClass(obj, targetClass, result);
					}
				}

				return result;
			}
		}

		log.info("❌ Package not found.");
		return List.of();
	}

	private void findInstancesOfEClass(EObject root, EClass targetClass, List<EObject> matches) {
		if (root.eClass() == targetClass) {
			if (!matches.contains(root)) {
				matches.add(root); // add only if not seen
			}
		}
		for (EObject child : root.eContents()) {
			findInstancesOfEClass(child, targetClass, matches);
		}
	}



	/**----------------------------------------------------------------------------------
	 * Handles EMF references (single-valued and multi-valued) during JSON serialization.
	 *-----------------------------------------------------------------------------------
	 * Rationale:
	 * Jackson cannot directly serialize EMF-specific types (e.g., {@code EcoreEList}, {@code DynamicEObjectImpl}).
	 * To make the output JSON-friendly, each {@link org.eclipse.emf.ecore.EObject} is converted into a
	 * {@code Map<String, Object>} using the existing {@code eObjectToMap(...)} helper, recursively
	 * expanding containment content and representing references/lists in a serializable form.
	 *
	 * Output:
	 * Produces a fully JSON-serializable structure (maps/lists/primitives) that can be returned by the API.
	 */

	public static Map<String, Object> eObjectToMap(EObject obj) {
		return eObjectToMap(obj, new HashSet<>());
	}

	private static Map<String, Object> eObjectToMap(EObject obj, Set<EObject> visited) {
		Map<String, Object> map = new LinkedHashMap<>();

		// Avoid recursion on cyclic references
		if (visited.contains(obj)) {
			map.put("ref", obj.eClass().getName() + " (already visited)");
			return map;
		}
		visited.add(obj);

		map.put("eClass", obj.eClass().getName());

		for (EStructuralFeature feature : obj.eClass().getEAllStructuralFeatures()) {
			Object value = obj.eGet(feature);

			if (value instanceof EObject nested) {
				map.put(feature.getName(), eObjectToMap(nested, visited));  // recursive with cycle protection
			} else if (value instanceof List<?> list) {
				List<Object> processedList = list.stream().map(item -> {
					if (item instanceof EObject eo) {
						return eObjectToMap(eo, visited);  // recursive with cycle protection
					}
					return item;
				}).toList();
				map.put(feature.getName(), processedList);
			} else {
				map.put(feature.getName(), value);
			}
		}

		return map;
	}



	/*--------------------------------------------------
	 * Method to handle references and lists of each class
	 * Json Serialization. 
	 * Jackson can’t serialize EMF objects like EcoreEList, DynamicEObjectImpl, etc. We convert an EObject to a Map<String, Object> using your eObjectToMap
	 */
	public static Map<String, Object> eObjectToMapClass(EObject obj) {
		return eObjectToMapClass(obj, new HashSet<>());
	}

	private static Map<String, Object> eObjectToMapClass(EObject obj, Set<EObject> visited) {
		Map<String, Object> map = new LinkedHashMap<>();

		if (visited.contains(obj)) {
			map.put("ref", obj.eClass().getName() + " (already visited)");
			return map;
		}
		visited.add(obj);

		map.put("eClass", obj.eClass().getName());

		for (EStructuralFeature feature : obj.eClass().getEAllStructuralFeatures()) {
			Object value = obj.eGet(feature);

			if (value instanceof EObject nested) {
				if (feature instanceof EReference ref && ref.isContainment()) {
					// ✅ Containment → expand fully
					map.put(feature.getName(), eObjectToMapClass(nested, visited));
				} else {
					// ✅ Non-containment → shallow
					map.put(feature.getName(), Map.of(
							"eClass", nested.eClass().getName()
							));
				}
			}
			else if (value instanceof List<?> list) {
				List<Object> processedList = list.stream().map(item -> {
					if (item instanceof EObject eo) {
						if (feature instanceof EReference ref && ref.isContainment()) {
							return eObjectToMapClass(eo, visited);
						} else {
							return Map.of("eClass", eo.eClass().getName());
						}
					}
					return item;
				}).toList();
				map.put(feature.getName(), processedList);
			}
			else {
				map.put(feature.getName(), value);
			}
		}

		return map;
	}


	/**-------------------------------------------------------------------------
	 * Retrieves all instances of a given EClass from a specific XMI model file
	 * -------------------------------------------------------------------------
	 */
	public List<EObject> getInstancesOfClassFromSpecificFile(
			String packageName,
			String className,
			String modelFileName
			) {
		List<EObject> result = new ArrayList<>();

		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}

				if (targetClass == null) {
					log.info("❌ Class not found in package.");
					return List.of();
				}

				String nsUri = matchedPackage.getNsURI();
				List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

				// ✅ Filter only the requested model file
				Optional<File> matchFile = xmiFiles.stream()
						.filter(f -> f.getName().equals(modelFileName))
						.findFirst();

				if (matchFile.isPresent()) {
					File xmiFile = matchFile.get();

					Resource resource = RepositoryScanner.resourceSet.getResource(
							URI.createFileURI(xmiFile.getAbsolutePath()), true
							);

					for (EObject obj : resource.getContents()) {
						findInstancesOfEClass(obj, targetClass, result);
					}

					return result;

				} else {
					log.info("❌ Specified model file not found: " + modelFileName);
					return List.of();
				}
			}
		}

		log.info("❌ Package not found.");
		return List.of();
	}


	/**--------------------------------------
	 * Fetch instances of class by attributes
	 * 	-------------------------------------
	 * Retrieves instances of a given EClass that match
	 * the provided attribute-based filters (i.e., fetch-by-attributes criteria).
	 */
	public List<EObject> findInstancesOfClassWithAttribute(
			String packageName,
			String className,
			String attributeName,
			String expectedValue
			) {
		List<EObject> result = new ArrayList<>();

		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}
				if (targetClass == null) {
					log.info("❌ Class not found.");
					return List.of();
				}

				// Find the EAttribute by name
				EAttribute attr = null;
				for (EAttribute a : targetClass.getEAllAttributes()) {
					if (a.getName().equals(attributeName)) {
						attr = a;
						break;
					}
				}
				if (attr == null) {
					log.info("❌ Attribute not found: " + attributeName);
					return List.of();
				}

				String nsUri = matchedPackage.getNsURI();
				List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

				for (File xmiFile : xmiFiles) {
					Resource resource = RepositoryScanner.resourceSet.getResource(
							URI.createFileURI(xmiFile.getAbsolutePath()), true
							);

					for (EObject obj : resource.getContents()) {
						findMatchingInstances(obj, targetClass, attr, expectedValue, result);
					}
				}

				return result;
			}
		}

		log.info("❌ Package not found.");
		return List.of();
	}

	private void findMatchingInstances(
			EObject root,
			EClass targetClass,
			EAttribute attr,
			String expectedValue,
			List<EObject> matches
			) {
		if (root.eClass() == targetClass) {
			Object val = root.eGet(attr);
			if (val != null && val.toString().equals(expectedValue)) {
				matches.add(root);
			}
		}
		for (EObject child : root.eContents()) {
			findMatchingInstances(child, targetClass, attr, expectedValue, matches);
		}
	}



	/**-----------------------------------
	 * Advanced Search
	 * -----------------------------------
	 */
	public List<EObject> findInstancesWithAttributesAdvanced(
			String packageName,
			String className,
			Map<String, String> attributeFilters
			) {
		List<EObject> result = new ArrayList<>();

		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}
				if (targetClass == null) {
					log.info("❌ Class not found.");
					return List.of();
				}

				String nsUri = matchedPackage.getNsURI();
				List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

				for (File xmiFile : xmiFiles) {
					Resource resource = RepositoryScanner.resourceSet.getResource(
							URI.createFileURI(xmiFile.getAbsolutePath()), true
							);

					for (EObject obj : resource.getContents()) {
						findMatchingInstancesAdvanced(obj, targetClass, attributeFilters, result);
					}
				}

				return result;
			}
		}

		log.info("❌ Package not found.");
		return List.of();
	}

	private void findMatchingInstancesAdvanced(
			EObject root,
			EClass targetClass,
			Map<String, String> attributeFilters,
			List<EObject> matches
			) {
		if (root.eClass() == targetClass) {
			boolean allMatch = true;

			for (Map.Entry<String, String> entry : attributeFilters.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();

				boolean isContains = false;
				String attrName = key;

				if (key.endsWith("_contains")) {
					isContains = true;
					attrName = key.substring(0, key.length() - "_contains".length());
				}

				EAttribute attr = null;
				for (EAttribute a : targetClass.getEAllAttributes()) {
					if (a.getName().equals(attrName)) {
						attr = a;
						break;
					}
				}

				if (attr == null) {
					allMatch = false;
					break;
				}

				Object attrValue = root.eGet(attr);
				if (attrValue == null) {
					allMatch = false;
					break;
				}

				String attrStr = attrValue.toString();
				if (isContains) {
					if (!attrStr.contains(value)) {
						allMatch = false;
						break;
					}
				} else {
					if (!attrStr.equals(value)) {
						allMatch = false;
						break;
					}
				}
			}

			if (allMatch) {
				matches.add(root);
			}
		}

		for (EObject child : root.eContents()) {
			findMatchingInstancesAdvanced(child, targetClass, attributeFilters, matches);
		}
	}


	/**--------------------------------------------------------------------------------------
	 * Deletes a model element by matching on class + attribute value within an XMI resource.
	 *---------------------------------------------------------------------------------------
	 * Note:
	 * EMF models are file-based (not database-backed), so "delete" means:
	 * 1) Load the target .xmi resource.
	 * 2) Find the matching {@link org.eclipse.emf.ecore.EObject} by EClass and attribute criteria.
	 * 3) Remove the EObject from its parent containment feature (or from the resource contents if it is a root).
	 * 4) Save the updated resource back to disk.
	 */

	public boolean deleteInstancesByAttribute(
			String packageName,
			String className,
			String attributeName,
			String valueToMatch,
			String modelFileName  // optional: if you want to limit to one xmi file
			) {
		boolean deletedAny = false;

		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {
				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}
				if (targetClass == null) {
					log.info("❌ Class not found.");
					return false;
				}

				EAttribute attr = null;
				for (EAttribute a : targetClass.getEAllAttributes()) {
					if (a.getName().equals(attributeName)) {
						attr = a;
						break;
					}
				}
				if (attr == null) {
					log.info("❌ Attribute not found: " + attributeName);
					return false;
				}

				String nsUri = matchedPackage.getNsURI();
				List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

				for (File xmiFile : xmiFiles) {
					if (modelFileName != null && !xmiFile.getName().equals(modelFileName)) {
						continue; // skip other files
					}

					Resource resource = RepositoryScanner.resourceSet.getResource(
							URI.createFileURI(xmiFile.getAbsolutePath()), true
							);

					boolean changed = false;

					// Recursively remove matching EObjects
					Iterator<EObject> iter = resource.getAllContents();
					while (iter.hasNext()) {
						EObject obj = iter.next();
						if (obj.eClass() == targetClass) {
							Object attrVal = obj.eGet(attr);
							if (attrVal != null && attrVal.toString().equals(valueToMatch)) {
								// Remove from parent containment
								EObject parent = obj.eContainer();
								if (parent != null) {
									EReference containingFeature = obj.eContainmentFeature();
									if (containingFeature.isMany()) {
										@SuppressWarnings("unchecked")
										List<EObject> siblings = (List<EObject>) parent.eGet(containingFeature);
										siblings.remove(obj);
									} else {
										parent.eUnset(containingFeature);
									}
								} else {
									// Root-level: remove from resource contents
									resource.getContents().remove(obj);
								}
								changed = true;
								deletedAny = true;
							}
						}
					}

					if (changed) {
						try {
							resource.save(null);
							log.info("✅ Saved updated XMI: " + xmiFile.getName());
						} catch (IOException e) {
							e.printStackTrace();
							return false;
						}
					}
				}

				return deletedAny;
			}
		}

		log.info("❌ Package not found.");
		return false;
	}


	/**------------------------------------------------------------------------------------
	 * Updates instances of a given EClass by matching an attribute value and replacing it.
	 *-------------------------------------------------------------------------------------
	 * Workflow:
	 * 1) Resolve the target EPackage by {@code packageName} from {@code RepositoryScanner.nsUriToEPackage}.
	 * 2) Resolve the target EClass ({@code className}) and EAttribute ({@code attributeName}); fail fast if not found.
	 * 3) Retrieve all XMI model files associated with the package {@code nsURI}.
	 * 4) For each XMI file (optionally restricted by {@code modelFileName}):
	 *    - load the EMF {@link org.eclipse.emf.ecore.resource.Resource}
	 *    - iterate all contents and select objects whose {@code eClass() == targetClass}
	 *    - when {@code attributeName} equals {@code oldValue}, set it to {@code newValue}
	 *    - save the resource back to disk only if changes were made
	 */

	public boolean updateInstancesByAttribute(
			String packageName,
			String className,
			String attributeName,
			String oldValue,
			String newValue,
			String modelFileName // optional
			) {
		boolean updatedAny = false;

		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}
				if (targetClass == null) {
					log.info("❌ Class not found.");
					return false;
				}

				EAttribute attr = null;
				for (EAttribute a : targetClass.getEAllAttributes()) {
					if (a.getName().equals(attributeName)) {
						attr = a;
						break;
					}
				}
				if (attr == null) {
					log.info("❌ Attribute not found: " + attributeName);
					return false;
				}

				String nsUri = matchedPackage.getNsURI();
				List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

				for (File xmiFile : xmiFiles) {
					if (modelFileName != null && !xmiFile.getName().equals(modelFileName)) {
						continue; // skip others
					}

					Resource resource = RepositoryScanner.resourceSet.getResource(
							URI.createFileURI(xmiFile.getAbsolutePath()), true
							);

					boolean changed = false;

					Iterator<EObject> iter = resource.getAllContents();
					while (iter.hasNext()) {
						EObject obj = iter.next();
						if (obj.eClass() == targetClass) {
							Object attrVal = obj.eGet(attr);
							if (attrVal != null && attrVal.toString().equals(oldValue)) {
								obj.eSet(attr, newValue);
								changed = true;
								updatedAny = true;
							}
						}
					}

					if (changed) {
						try {
							resource.save(null);
							log.info("✅ Saved updated XMI: " + xmiFile.getName());
						} catch (IOException e) {
							e.printStackTrace();
							return false;
						}
					}
				}

				return updatedAny;
			}
		}

		log.info("❌ Package not found.");
		return false;
	}



	/**----------------------------------------------------------------------------------------------
	 * Inserts a new EMF instance ({@link org.eclipse.emf.ecore.EObject}) into a specific XMI file.
	 *-----------------------------------------------------------------------------------------------
	 * Workflow steps:
	 * 1) Ensure the repository exists and metamodels are loaded.
	 * 2) Resolve the target {@link org.eclipse.emf.ecore.EPackage} from the in-memory registry
	 *    (accepting package name / nsPrefix / nsURI).
	 * 3) Resolve the target {@link org.eclipse.emf.ecore.EClass} ({@code className}); fail fast if not found.
	 * 4) Locate the target XMI file ({@code modelFileName}) using the package {@code nsURI} association
	 *    (fallback to repository directory lookup if needed).
	 * 5) Prepare the {@link org.eclipse.emf.ecore.resource.ResourceSet} for XMI I/O:
	 *    - register all loaded EPackages in the package registry (prevents “package not found” by nsURI)
	 *    - ensure the XMI resource factory is available.
	 * 6) Load the XMI {@link org.eclipse.emf.ecore.resource.Resource}.
	 * 7) Create a new EObject via the package factory and apply attribute values:
	 *    - resolve {@link org.eclipse.emf.ecore.EAttribute} by name
	 *    - convert from string using {@link org.eclipse.emf.ecore.util.EcoreUtil#createFromString}
	 *    - set the converted value; unknown attributes are skipped.
	 * 8) Insert the new instance into the model safely:
	 *    - if the resource has no root, add it as root
	 *    - otherwise update the existing root (same class) or attach via a compatible containment reference
	 *      to avoid multiple roots and <xmi:XMI> wrapping.
	 * 9) Save the updated XMI back to disk (e.g., UTF-8 and optional schemaLocation).
	 *
	 * @return {@code true} if the instance was inserted and saved successfully; {@code false} otherwise.
	 */
	
	public boolean insertNewInstance(
	        String packageName,
	        String className,
	        Map<String, String> attributeValues,
	        String modelFileName
	) {
	    // 0) Ensure repository exists
	    File repoDir = new File(RepositoryScanner.REPO_PATH);
	    if (!repoDir.exists()) {
	        // avoid System.out in MCP mode; use logger if possible
	        return false;
	    }

	    // 1) Ensure metamodels are loaded (and registry maps are populated)
	    if (RepositoryScanner.nsUriToEPackage.isEmpty()) {
	        new RepositoryScanner().scanRepository(repoDir);
	    }

	    // 2) Resolve EPackage by name OR nsURI OR nsPrefix (robust)
	    EPackage matchedPackage = RepositoryScanner.nsUriToEPackage.values().stream()
	            .filter(pkg ->
	                    packageName.equals(pkg.getName()) ||
	                    packageName.equals(pkg.getNsURI()) ||
	                    packageName.equals(pkg.getNsPrefix()))
	            .findFirst()
	            .orElse(null);

	    if (matchedPackage == null) {
	        return false;
	    }

	    // 3) Resolve EClass inside that package
	    EClassifier c = matchedPackage.getEClassifier(className);
	    if (!(c instanceof EClass targetClass)) {
	        return false;
	    }

	    // 4) Resolve the model file (prefer your nsUriToXmiFiles map, fallback to repoDir)
	    String nsUri = matchedPackage.getNsURI();
	    List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

	    boolean foundInRegistry = xmiFiles.stream()
	            .anyMatch(f -> f.getName().equals(modelFileName));

	    File xmiFile = xmiFiles.stream()
	            .filter(f -> f.getName().equals(modelFileName))
	            .findFirst()
	            .orElseGet(() -> new File(repoDir, modelFileName));

	    if (!xmiFile.exists()) {
	        return false;
	    }

	    // If found via fallback (not in registry), register it now
	    if (!foundInRegistry) {
	        RepositoryScanner.nsUriToXmiFiles
	                .computeIfAbsent(nsUri, k -> new ArrayList<>())
	                .add(xmiFile);
	        log.info("Registered fallback model file: " + xmiFile.getAbsolutePath());
	    }

	    // 5) Make sure the ResourceSet can resolve packages by nsURI (prevents "package not found")
	    //    IMPORTANT: this is what fixes the "Package with uri ... not found" issue at load time.
	    RepositoryScanner.resourceSet.getPackageRegistry().putAll(RepositoryScanner.nsUriToEPackage);
	    RepositoryScanner.nsUriToEPackage.forEach((uri, pkg) -> EPackage.Registry.INSTANCE.put(uri, pkg));

	    // Ensure XMI factory exists
	    RepositoryScanner.resourceSet.getResourceFactoryRegistry()
	            .getExtensionToFactoryMap()
	            .putIfAbsent("xmi", new org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl());

	    // 6) Load the XMI resource
	    Resource resource = RepositoryScanner.resourceSet.getResource(
	            URI.createFileURI(xmiFile.getAbsolutePath()), true
	    );

	    // 7) Create new EObject
	    EObject newObject = matchedPackage.getEFactoryInstance().create(targetClass);

	    // 8) Set attributes safely (type conversion using EcoreUtil)
	    for (Map.Entry<String, String> entryAttr : attributeValues.entrySet()) {
	        String attrName = entryAttr.getKey();
	        String rawValue = entryAttr.getValue();

	        EStructuralFeature sf = targetClass.getEStructuralFeature(attrName);
	        if (!(sf instanceof EAttribute attr)) {
	            // unknown attribute -> ignore (or return false if you prefer strict)
	            continue;
	        }

	        try {
	            EDataType dt = attr.getEAttributeType();
	            Object converted = org.eclipse.emf.ecore.util.EcoreUtil.createFromString(dt, rawValue);
	            newObject.eSet(attr, converted);
	        } catch (Exception ex) {
	            return false; // or continue, depending on how strict you want to be
	        }
	    }

	    // 9) Insert logic: avoid multiple roots (prevents <xmi:XMI> wrapper)
	    if (resource.getContents().isEmpty()) {
	        // No root -> new object becomes root
	        resource.getContents().add(newObject);
	    } else {
	        EObject root = resource.getContents().get(0);

	        // 9A) If the root is the same EClass, update the existing root instead of adding a 2nd root
	        if (root.eClass() == targetClass) {
	            for (Map.Entry<String, String> entryAttr : attributeValues.entrySet()) {
	                EStructuralFeature sf = root.eClass().getEStructuralFeature(entryAttr.getKey());
	                if (sf instanceof EAttribute attr) {
	                    Object converted = org.eclipse.emf.ecore.util.EcoreUtil.createFromString(
	                            (EDataType) attr.getEAttributeType(), entryAttr.getValue()
	                    );
	                    root.eSet(attr, converted);
	                }
	            }
	        } else {
	            // 9B) Otherwise, try to attach as a containment child under the existing root
	            // Find a containment reference on root that can contain the new object's EClass
	            EReference containmentRef = root.eClass().getEAllContainments().stream()
	                    .filter(ref -> ref.getEReferenceType().isSuperTypeOf(targetClass) || ref.getEReferenceType() == targetClass)
	                    .findFirst()
	                    .orElse(null);

	            if (containmentRef == null) {
	                // No valid place to attach. If you add as root, you'll get <xmi:XMI> with multiple roots.
	                return false;
	            }

	            if (containmentRef.isMany()) {
	                @SuppressWarnings("unchecked")
	                List<EObject> list = (List<EObject>) root.eGet(containmentRef);
	                list.add(newObject);
	            } else {
	                root.eSet(containmentRef, newObject);
	            }
	        }
	    }

	    // 10) Save
	    try {
	        Map<Object, Object> options = new HashMap<>();
	        options.put(org.eclipse.emf.ecore.xmi.XMLResource.OPTION_ENCODING, "UTF-8");
	        options.put(org.eclipse.emf.ecore.xmi.XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
	        resource.save(options);
	        log.info("✅ Inserted & saved new instance: " + newObject);
	        return true;
	    } catch (IOException e) {
	        return false;
	    }
	}

	/*
	public boolean insertNewInstance(
			String packageName,
			String className,
			Map<String, String> attributeValues,
			String modelFileName
			) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, packageName);
			if (matchedPackage != null) {

				EClass targetClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(className)) {
						targetClass = ec;
						break;
					}
				}
				if (targetClass == null) {
					log.info("❌ Class not found: " + className);
					return false;
				}

				String nsUri = matchedPackage.getNsURI();
				List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

				Optional<File> matchFile = xmiFiles.stream()
						.filter(f -> f.getName().equals(modelFileName))
						.findFirst();

				if (matchFile.isEmpty()) {
					log.info("❌ XMI file not found: " + modelFileName);
					return false;
				}

				File xmiFile = matchFile.get();
				Resource resource = RepositoryScanner.resourceSet.getResource(
						URI.createFileURI(xmiFile.getAbsolutePath()), true
						);

				EObject newObject = matchedPackage.getEFactoryInstance().create(targetClass);

				// Set attributes safely
				for (Map.Entry<String, String> entryAttr : attributeValues.entrySet()) {
					String attrName = entryAttr.getKey();
					String attrValue = entryAttr.getValue();

					EAttribute attr = null;
					for (EAttribute a : targetClass.getEAllAttributes()) {
						if (a.getName().equals(attrName)) {
							attr = a;
							break;
						}
					}

					if (attr != null) {
						newObject.eSet(attr, attrValue);
					} else {
						log.info("⚠️ Unknown attribute: " + attrName);
					}
				}

				// ✅ Add new object as root-level content (or customize)
				resource.getContents().add(newObject);

				try {
					resource.save(null);
					log.info("✅ Inserted & saved new instance: " + newObject);
					return true;
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
		}

		log.info("❌ Package not found.");
		return false;
	}
*/
	//End insertNewInstance()

	/**
	 * Inserts a new child {@link org.eclipse.emf.ecore.EObject} into an existing container in an XMI model.
	 *
	 * The caller specifies:
	 * - the parent package + parent class
	 * - an optional parent selector (attribute name + value) to locate the exact parent instance
	 * - the containment reference name on the parent (e.g., {@code children}, {@code members}, {@code sons})
	 * - the child class and its attribute values
	 * - the target XMI file to update
	 *
	 * Workflow:
	 * 1) Resolve the target {@link org.eclipse.emf.ecore.EPackage} and {@link org.eclipse.emf.ecore.EClass} for the parent.
	 * 2) Load the requested XMI {@link org.eclipse.emf.ecore.resource.Resource}.
	 * 3) Locate the parent instance:
	 *    - if no parent attribute filter is provided, use the first matching parent object
	 *    - otherwise, match {@code parentAttrName == parentAttrValue}
	 * 4) Resolve the containment {@link org.eclipse.emf.ecore.EReference} on the parent by {@code containmentRefName}.
	 * 5) Create the child EObject (child class) via the package factory.
	 * 6) Set child attributes with type-safe conversion from raw strings
	 *    (via {@code ModelUtils.rawStringToAttributeType(...)}).
	 * 7) Attach the child under the parent containment reference:
	 *    - if multi-valued, add to the list
	 *    - if single-valued, set directly
	 * 8) Save the updated XMI back to disk.
	 *
	 * @return {@code true} if the child was inserted and the resource saved successfully; {@code false} otherwise.
	 */

	/*
	 * This method deals with data type safe conversion
	 * 
	 */
	public boolean insertIntoContainer(
			String parentPackage,
			String parentClass,
			String parentAttrName,   // optional
			String parentAttrValue,  // optional
			String containmentRefName,
			String childClass,
			Map<String, String> childAttributes,
			String modelFileName
			) {
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			EPackage rootPackage = entry.getValue();
			EPackage matchedPackage = findPackageByName(rootPackage, parentPackage);
			if (matchedPackage != null) {

				EClass parentEClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(parentClass)) {
						parentEClass = ec;
						break;
					}
				}
				if (parentEClass == null) {
					log.info("❌ Parent class not found: " + parentClass);
					return false;
				}

				String nsUri = matchedPackage.getNsURI();
				List<File> xmiFiles = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

				Optional<File> matchFile = xmiFiles.stream()
						.filter(f -> f.getName().equals(modelFileName))
						.findFirst();

				if (matchFile.isEmpty()) {
					log.info("❌ XMI file not found: " + modelFileName);
					return false;
				}

				File xmiFile = matchFile.get();
				Resource resource = RepositoryScanner.resourceSet.getResource(
						URI.createFileURI(xmiFile.getAbsolutePath()), true
						);

				EAttribute parentAttr = null; // ✅ declared once

				EObject parentInstance = null;
				Iterator<EObject> iter = resource.getAllContents();
				while (iter.hasNext()) {
					EObject obj = iter.next();
					if (obj.eClass() == parentEClass) {

						if (parentAttrName == null || parentAttrValue == null) {
							// No filter: take first match
							parentInstance = obj;
							log.info("ℹ️ No parent attribute filter, using first parent: " + parentEClass.getName());
							break;
						} else {
							if (parentAttr == null) {
								for (EAttribute a : parentEClass.getEAllAttributes()) {
									if (a.getName().equals(parentAttrName)) {
										parentAttr = a;
										break;
									}
								}
								if (parentAttr == null) {
									log.info("❌ Parent attribute not found: " + parentAttrName);
									return false;
								}
							}

							Object val = obj.eGet(parentAttr);
							if (val != null && val.toString().equals(parentAttrValue)) {
								parentInstance = obj;
								log.info("✅ Found parent instance with " + parentAttrName + "=" + parentAttrValue);
								break;
							}
						}
					}
				}

				if (parentInstance == null) {
					log.info("❌ Parent instance not found.");
					return false;
				}

				// Find containment reference
				EReference containmentRef = null;
				for (EReference ref : parentEClass.getEAllContainments()) {
					if (ref.getName().equals(containmentRefName)) {
						containmentRef = ref;
						break;
					}
				}
				if (containmentRef == null) {
					log.info("❌ Containment reference not found: " + containmentRefName);
					return false;
				}

				// Create child EObject
				EClass childEClass = null;
				for (EClassifier classifier : matchedPackage.getEClassifiers()) {
					if (classifier instanceof EClass ec && ec.getName().equals(childClass)) {
						childEClass = ec;
						break;
					}
				}
				if (childEClass == null) {
					log.info("❌ Child class not found: " + childClass);
					return false;
				}

				EObject child = matchedPackage.getEFactoryInstance().create(childEClass);

				for (Map.Entry<String, String> attrEntry : childAttributes.entrySet()) {
					String attrName = attrEntry.getKey();
					String attrVal = attrEntry.getValue();

					EAttribute childAttr = null;
					for (EAttribute a : childEClass.getEAllAttributes()) {
						if (a.getName().equals(attrName)) {
							childAttr = a;
							break;
						}
					}

					/** previous
					 * 
		                if (childAttr != null) {
		                    child.eSet(childAttr, attrVal);
		                } else {
		                    log.info("⚠️ Unknown child attribute: " + attrName);
		                } */

					/*
					 * This converts a raw string input (attrVal) into the correct Java type expected by the EMF attribute (childAttr).
							For example: If childAttr is an EAttribute of type int, and attrVal is "25", 
							it converts the string "25" to the Integer value 25.
    						If it's a Date, it will attempt to parse the string to a Date object.
					 */
					Object convertedValue = ModelUtils.rawStringToAttributeType(childAttr, attrVal);
					if (convertedValue != null) {
						child.eSet(childAttr, convertedValue);
					} else {
						log.info("⚠️ Skipped setting attribute due to conversion issue: " + attrName);
					}//end raw string input to the corresponding data types

				}

				// ✅ Add child to parent’s containment reference
				if (containmentRef.isMany()) {
					@SuppressWarnings("unchecked")
					List<EObject> children = (List<EObject>) parentInstance.eGet(containmentRef);
					children.add(child);
				} else {
					parentInstance.eSet(containmentRef, child);
				}

				try {
					resource.save(null);
					log.info("✅ Child inserted and saved in: " + modelFileName);
					return true;
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				}
			}
		}

		log.info("❌ Package not found: " + parentPackage);
		return false;
	}


	/**---------------------------------------
	 * Create new XMI file automatically
	 * -------------------------------------
	 */
	public boolean createNewXmiFile(String packageName, String rootClassName, String fileName) {
		// ✅ 1. Ensure the repository path exists
		File repoDir = new File(RepositoryScanner.REPO_PATH);
		if (!repoDir.exists()) {
			log.info("❌ Repository path does not exist: " + RepositoryScanner.REPO_PATH);
			return false;
		}

		// ✅ 2. Load and register all Ecore metamodels if not already done
		if (RepositoryScanner.nsUriToEPackage.isEmpty()) {
			log.info("ℹ️ Metamodel registry is empty — scanning repository...");
			new RepositoryScanner().scanRepository(repoDir);
		} else {
			log.info("✅ Metamodels already loaded.");
		}

		// ✅ 3. Debug all loaded packages
		log.info("🔍 Registered Metamodels in RepoScanner:");
		RepositoryScanner.nsUriToEPackage.forEach((ns, pkg) ->
		log.info(" - " + pkg.getName() + " => " + ns)
				);

		log.info("📦 Registered in EPackage.Registry.INSTANCE:");
		EPackage.Registry.INSTANCE.forEach((ns, pkg) ->
		log.info(" - " + ns)
				);

		// ✅ 4. Find the matching EPackage by name
		EPackage matched = RepositoryScanner.nsUriToEPackage.values().stream()
				.filter(pkg -> pkg.getName().equals(packageName)
						|| packageName.equals(pkg.getNsURI())
				        || packageName.equals(pkg.getNsPrefix())
						)
				.findFirst()
				.orElse(null);

		if (matched == null) {
			log.info("❌ Package not found: " + packageName);
			return false;
		}
		log.info("✅ Found package: " + matched.getName() + " (" + matched.getNsURI() + ")");

		// ✅ 5. Find the root EClass
		EClass rootClass = (EClass) matched.getEClassifier(rootClassName);
		if (rootClass == null) {
			log.info("❌ Root class not found: " + rootClassName);
			return false;
		}
		log.info("✅ Found root class: " + rootClass.getName());

		// ✅ 6. Create a new root EObject
		EObject rootObject = matched.getEFactoryInstance().create(rootClass);

		// ✅ 7. Use a new ResourceSet linked to global registry
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.setPackageRegistry(EPackage.Registry.INSTANCE); // 🔥 Critical line

		// ✅ 8. Register resource factories (just in case)
		Resource.Factory.Registry factoryRegistry = Resource.Factory.Registry.INSTANCE;
		factoryRegistry.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
		factoryRegistry.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());

		// ✅ 9. Create the XMI resource and add root object
		File xmiFile = new File(RepositoryScanner.REPO_PATH, fileName);
		URI fileUri = URI.createFileURI(xmiFile.getAbsolutePath());
		Resource resource = resourceSet.createResource(fileUri);
		resource.getContents().add(rootObject);

		// ✅ 10. Save with UTF-8 encoding
		try {
			Map<Object, Object> options = new HashMap<>();
			options.put(XMLResource.OPTION_ENCODING, "UTF-8");

			log.info("📦 Checking global EPackage.Registry before save:");
			EPackage.Registry.INSTANCE.forEach((ns, pkg) ->
			log.info(" - " + ns)
					);

			resource.save(options);

			// Register the new file in RepositoryScanner so query/update/delete tools can find it
			String nsUri = matched.getNsURI();
			RepositoryScanner.nsUriToXmiFiles
					.computeIfAbsent(nsUri, k -> new ArrayList<>())
					.add(xmiFile);
			RepositoryScanner.resourceSet.getResource(
					URI.createFileURI(xmiFile.getAbsolutePath()), true);

			log.info("✅ XMI file created and registered: " + xmiFile.getAbsolutePath());
			return true;

		} catch (IOException e) {
			System.err.println("❌ Failed to save XMI file: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	//end


	/**---------------------------------------------------------
	 * Get a list of Packages and their corresponding XMI Files
	 * ---------------------------------------------------------
	 */
	public List<Map<String, Object>> listPackagesWithXmiFiles() {
		List<Map<String, Object>> result = new ArrayList<>();

		// Build response from nsURI maps
		for (Map.Entry<String, EPackage> entry : RepositoryScanner.nsUriToEPackage.entrySet()) {
			String nsUri = entry.getKey();
			EPackage pkg = entry.getValue();

			List<File> files = RepositoryScanner.nsUriToXmiFiles.getOrDefault(nsUri, List.of());

			List<Map<String, String>> xmiFiles = new ArrayList<>();
			for (File f : files) {
				xmiFiles.add(Map.of(
						"fileName", f.getName(),
						"absolutePath", f.getAbsolutePath()
						));
			}

			Map<String, Object> pkgMap = new LinkedHashMap<>();
			pkgMap.put("name", pkg.getName());
			pkgMap.put("nsPrefix", pkg.getNsPrefix());
			pkgMap.put("nsURI", pkg.getNsURI());
			pkgMap.put("xmiFiles", xmiFiles);

			result.add(pkgMap);
		}

		// Optional: sort packages by name
		result.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));

		return result;
	}


	/**------------------------------------------
	 * Reads an XMI model and returns it as JSON.
	 * ------------------------------------------
	 * Returns only the root object(s) of the XMI resource.
	 * Recursively expands containment references to produce a tree-like JSON structure.
	 */

	public List<Map<String, Object>> readXmiAsJson(String modelFileName) {
		ensureRepoScanned();

		// Use a dedicated ResourceSet linked to the global registry
		ResourceSet rs = new ResourceSetImpl();
		rs.setPackageRegistry(EPackage.Registry.INSTANCE);

		// Ensure XMI factory registered
		rs.getResourceFactoryRegistry()
		.getExtensionToFactoryMap()
		.put("xmi", new XMIResourceFactoryImpl());

		File xmiFile = resolveModelFileInRepo(modelFileName);

		// Load resource
		Resource res = rs.getResource(URI.createFileURI(xmiFile.getAbsolutePath()), true);

		// Convert only root contents (hierarchical)
		List<Map<String, Object>> out = new ArrayList<>();

		// Shared identity maps across roots (stable refs across the whole response)
		Map<EObject, String> ids = new IdentityHashMap<>();
		Set<EObject> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

		for (EObject root : res.getContents()) {
			out.add(eObjectToJsonTree(root, ids, visiting));
		}
		return out;
	}

	/**
	 * Converts EObject into a JSON-safe tree:
	 * - Attributes -> primitives/strings/enums
	 * - Containment refs -> fully expanded child objects
	 * - Non-containment refs -> lightweight _ref object
	 * - Cycle-safe (prevents StackOverflow)
	 */
	private Map<String, Object> eObjectToJsonTree(EObject obj,
			Map<EObject, String> ids,
			Set<EObject> visiting) {

		Map<String, Object> map = new LinkedHashMap<>();

		// Stable id for references
		String id = ids.computeIfAbsent(obj,
				o -> o.eClass().getName() + "#" + System.identityHashCode(o));

		map.put("_id", id);
		//map.put("eClass", obj.eClass().getName());
		//map.put("nsURI", obj.eClass().getEPackage().getNsURI());

		// Cycle protection: if we're already expanding this object in the current path
		if (visiting.contains(obj)) {
			map.put("_cycle", true);
			return map;
		}
		visiting.add(obj);

		// Attributes
		for (EAttribute a : obj.eClass().getEAllAttributes()) {
			Object v = obj.eGet(a);
			map.put(a.getName(), toJsonScalar(v));
		}

		// References
		for (EReference r : obj.eClass().getEAllReferences()) {
			Object v = obj.eGet(r);

			if (r.isMany()) {
				List<Object> arr = new ArrayList<>();
				if (v instanceof List<?> list) {
					for (Object item : list) {
						arr.add(referenceValue(item, r.isContainment(), ids, visiting));
					}
				}
				map.put(r.getName(), arr);
			} else {
				map.put(r.getName(), referenceValue(v, r.isContainment(), ids, visiting));
			}
		}

		// Done expanding this branch
		visiting.remove(obj);

		return map;
	}

	/**
	 * For containment references: expand recursively.
	 * For non-containment references: return lightweight ref.
	 */
	private Object referenceValue(Object v,
			boolean containment,
			Map<EObject, String> ids,
			Set<EObject> visiting) {

		if (!(v instanceof EObject eo)) {
			return toJsonScalar(v);
		}

		if (containment) {
			// Expand containment fully
			return eObjectToJsonTree(eo, ids, visiting);
		}


		// Non-containment => lightweight reference only (prevents duplication)
		return Map.of(
				"_ref", ids.computeIfAbsent(eo,
						o -> eo.eClass().getName() + "#" + System.identityHashCode(o))
				//"eClass", eo.eClass().getName(),
				//"nsURI", eo.eClass().getEPackage().getNsURI()
				);
	}

	/**
	 * Convert EMF values into JSON-safe scalars.
	 * - Enums -> literal
	 * - Dates -> string (or you can format)
	 * - Everything else -> string fallback
	 */
	private Object toJsonScalar(Object v) {
		if (v == null) return null;

		if (v instanceof String || v instanceof Number || v instanceof Boolean) return v;

		if (v instanceof EEnumLiteral lit) return lit.getLiteral();

		// Some EMF typed values may serialize weirdly; keep safe fallback
		return String.valueOf(v);
	}

	/**
	 * Security / correctness: resolve only from repository path,
	 * and only allow .xmi extension.
	 */

	/*
	 * Allows subdirectories: e.g. "cw/model1.xmi"
	 * Blocks traversal: "../" etc.
	 * Ensures file is inside REPO_PATH
	 */

	private File resolveModelFileInRepo(String modelFileName) {
		if (modelFileName == null || modelFileName.isBlank()) {
			throw new IllegalArgumentException("modelFileName is required.");
		}
		if (!modelFileName.toLowerCase().endsWith(".xmi")) {
			throw new IllegalArgumentException("Only .xmi files are allowed.");
		}

		Path repoRoot = Paths.get(RepositoryScanner.REPO_PATH).toAbsolutePath().normalize();

		// ✅ 1) Check directly in root folder first
		Path direct = repoRoot.resolve(modelFileName).normalize();
		if (direct.startsWith(repoRoot) && Files.exists(direct) && Files.isRegularFile(direct)) {
			return direct.toFile();
		}

		// ✅ 2) If not found, search recursively in subfolders
		try (Stream<Path> stream = Files.walk(repoRoot)) {
			List<Path> matches = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().equalsIgnoreCase(modelFileName))
					.collect(Collectors.toList());

			if (matches.isEmpty()) {
				throw new IllegalArgumentException("Model file not found in repository (root or subfolders): " + modelFileName);
			}

			if (matches.size() > 1) {
				// ⚠️ ambiguous: same filename exists in multiple folders
				String locations = matches.stream()
						.map(p -> repoRoot.relativize(p).toString())
						.collect(Collectors.joining(", "));
				throw new IllegalArgumentException(
						"Multiple XMI files found with the same name. Please rename or provide a unique filename. Matches: " + locations
						);
			}

			return matches.get(0).toFile();

		} catch (IOException e) {
			throw new RuntimeException("Failed to search repository: " + e.getMessage(), e);
		}
	}


	/**
	 * Ensure metamodels + XMI associations are loaded once.
	 * If you already do this at @PostConstruct elsewhere, you can simplify this.
	 */
	private void ensureRepoScanned() {
		if (RepositoryScanner.nsUriToEPackage.isEmpty()) {
			new RepositoryScanner().scanRepository(new File(RepositoryScanner.REPO_PATH));
		}
	}

	//End Read XmI as a JSON

	
	
	/**
	 * Transforms an XMI model into JSON and saves the JSON file in the repository.
	 *
	 * Example:
	 *   input:  Family.xmi
	 *   output: Family.json
	 *
	 * Returns metadata about the generated file.
	 */
	public Map<String, Object> transformXmiToJsonAndSave(String modelFileName) {
	    ensureRepoScanned();

	    // 1. Read XMI as hierarchical JSON
	    List<Map<String, Object>> jsonContent = readXmiAsJson(modelFileName);

	    // 2. Resolve input XMI file
	    File xmiFile = resolveModelFileInRepo(modelFileName);

	    // 3. Create output JSON file path in the same folder as the XMI
	    String jsonFileName = replaceExtension(xmiFile.getName(), "_sysml.json");
	    Path jsonPath = xmiFile.toPath().getParent().resolve(jsonFileName).normalize();

	    try {
	        // 4. Serialize JSON nicely
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.enable(SerializationFeature.INDENT_OUTPUT);

	        String jsonString = mapper.writeValueAsString(jsonContent);

	        // 5. Save JSON file
	        Files.writeString(
	            jsonPath,
	            jsonString,
	            StandardCharsets.UTF_8,
	            StandardOpenOption.CREATE,
	            StandardOpenOption.TRUNCATE_EXISTING
	        );

	        // 6. Return structured result
	        Map<String, Object> result = new LinkedHashMap<>();
	        result.put("sourceFile", xmiFile.getName());
	        result.put("outputFile", jsonFileName);
	        result.put("outputPath", Paths.get(RepositoryScanner.REPO_PATH)
	                .toAbsolutePath()
	                .normalize()
	                .relativize(jsonPath.toAbsolutePath().normalize())
	                .toString());
	        result.put("content", jsonContent);
	        result.put("message", "XMI successfully transformed to JSON and saved in repository.");

	        return result;

	    } catch (IOException e) {
	        throw new RuntimeException("Failed to save JSON file: " + e.getMessage(), e);
	    }
	}
	
	
	private String replaceExtension(String fileName, String newExtension) {
	    int dotIndex = fileName.lastIndexOf('.');
	    if (dotIndex == -1) {
	        return fileName + newExtension;
	    }
	    return fileName.substring(0, dotIndex) + newExtension;
	}
	//End TransformXmiToJsonAndSave
	
	
	
	/**
	 * List all JSON files in the repository.
	 * @param recursive if true, scan subfolders too
	 */
	public List<Map<String, Object>> listJsonFiles(boolean recursive) {
		File repoDir = new File(RepositoryScanner.REPO_PATH);
		if (!repoDir.exists() || !repoDir.isDirectory()) {
			throw new IllegalStateException("Repository directory not found: " + repoDir.getAbsolutePath());
		}

		List<File> files = new ArrayList<>();
		collectJsonFiles(repoDir, recursive, files);

		// Sort by name (or by lastModified if you prefer)
		files.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

		return files.stream()
				.map(f -> Map.<String, Object>of(
						"fileName", f.getName(),
						//"absolutePath", f.getAbsolutePath(),
						"sizeBytes", f.length(),
						"lastModified", f.lastModified()
						))
				.toList();
	}

	private void collectJsonFiles(File dir, boolean recursive, List<File> out) {
		File[] entries = dir.listFiles();
		if (entries == null) return;

		for (File f : entries) {
			if (f.isDirectory() && recursive) {
				collectJsonFiles(f, true, out);
			} else if (f.isFile() && f.getName().toLowerCase().endsWith(".json")) {
				out.add(f);
			}
		}
	}
	
	
	//---------------------------------------------
	//private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Read a JSON file from the repository and return it as a parsed object.
	 * - If JSON root is an object -> returns Map<String, Object>
	 * - If JSON root is an array  -> returns List<Object>
	 */
	public Object readJsonFileContent(String jsonFileName) {
		File file = resolveRepoFile(jsonFileName, ".json");

		try {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(file, Object.class);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read/parse JSON file: " + file.getAbsolutePath()
			+ " (" + e.getMessage() + ")", e);
		}
	}

	/** Convenience wrapper if you expect the JSON root to be an object. */
	@SuppressWarnings("unchecked")
	public Map<String, Object> readJsonAsMap(String jsonFileName) {
		Object obj = readJsonFileContent(jsonFileName);
		if (obj instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		throw new IllegalArgumentException("JSON root is not an object (expected Map): " + jsonFileName);
	}

	/** Convenience wrapper if you expect the JSON root to be an array. */
	@SuppressWarnings("unchecked")
	public List<Object> readJsonAsList(String jsonFileName) {
		Object obj = readJsonFileContent(jsonFileName);
		if (obj instanceof List<?> list) {
			return (List<Object>) list;
		}
		throw new IllegalArgumentException("JSON root is not an array (expected List): " + jsonFileName);
	}

	/* ---------------- helpers ---------------- */

	private File resolveRepoFile(String fileName, String requiredExt) {
		if (fileName == null || fileName.isBlank()) {
			throw new IllegalArgumentException("File name is required.");
		}

		String normalized = fileName.trim();
		if (requiredExt != null && !requiredExt.isBlank() && !normalized.toLowerCase().endsWith(requiredExt)) {
			normalized = normalized + requiredExt; // allow passing without extension
		}

		File repoDir = new File(RepositoryScanner.REPO_PATH);
		if (!repoDir.exists() || !repoDir.isDirectory()) {
			throw new IllegalStateException("Repository directory not found: " + repoDir.getAbsolutePath());
		}

		// Prevent path traversal like ../../secret
		Path repoPath = repoDir.toPath().toAbsolutePath().normalize();
		Path requested = repoPath.resolve(normalized).normalize();
		if (!requested.startsWith(repoPath)) {
			throw new IllegalArgumentException("Invalid filename (path traversal): " + fileName);
		}

		File f = requested.toFile();
		if (!f.exists() || !f.isFile()) {
			throw new IllegalArgumentException("JSON file not found in repository: " + f.getAbsolutePath());
		}

		return f;
	}
//	

}
