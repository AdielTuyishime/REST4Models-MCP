package org.gst.REST4Models10.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.gst.REST4Models10.repository.RepositoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelUtils {
	
	private static final Logger log = LoggerFactory.getLogger(RepositoryScanner.class);

	public static Map<String, Object> eObjectToMap(EObject obj) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("eClass", obj.eClass().getName());
		for (EStructuralFeature feature : obj.eClass().getEAllStructuralFeatures()) {
			Object value = obj.eGet(feature);
			map.put(feature.getName(), value);
		}
		return map;
	}

	/**
	 * This method converts a raw string input into their correct Java type expected by 
	 * the EMF attributes
	 * 
	 */
	public static Object rawStringToAttributeType(EAttribute attr, String rawValue) {
		EDataType dataType = attr.getEAttributeType();
		Class<?> instanceClass = dataType.getInstanceClass();

		if (instanceClass == null) {
			//System.out.println("⚠️ Instance class is null for attribute: " + attr.getName());
			log.info("⚠️ Instance class is null for attribute:" + attr.getName());
			return rawValue; // fallback to raw string
		}

		try {
			switch (instanceClass.getSimpleName()) {

			// ✅ Strings remain unchanged
			case "String":
				return rawValue;

				// ✅ Integer and int parsing
			case "Integer":
			case "int":
				return Integer.parseInt(rawValue);

				// ✅ Long and long parsing
			case "Long":
			case "long":
				return Long.parseLong(rawValue);

				// ✅ Float and float parsing
			case "Float":
			case "float":
				return Float.parseFloat(rawValue);

				// ✅ Double and double parsing
			case "Double":
			case "double":
				return Double.parseDouble(rawValue);

				// ✅ Short and short parsing
			case "Short":
			case "short":
				return Short.parseShort(rawValue);

				// ✅ Boolean and boolean parsing
			case "Boolean":
			case "boolean":
				return Boolean.parseBoolean(rawValue);

				// ✅ Basic Date parsing (can be extended)
				/*
                case "Date":
                    // You can customize this pattern to match your expected input
                    return new SimpleDateFormat("dd MMM yyyy").parse(rawValue);
				 */
			case "Date":
				try {
					return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(rawValue);
				} catch (ParseException e1) {
					try {
						return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(rawValue);
					} catch (ParseException e2) {
						throw new RuntimeException("Unsupported date format: " + rawValue);
					}
				}

			default:
				// Fallback: return raw value for unknown types
				log.info("⚠️ Unhandled type: " + instanceClass.getSimpleName() + " — using raw value.");
				return rawValue;
			}

		} catch (Exception e) {
			// Log any parsing or type conversion error
			log.info("❌ Failed to coerce '" + rawValue + "' to " + instanceClass.getSimpleName()
			+ " for attribute: " + attr.getName());
			e.printStackTrace();
			return null; // signal failure
		}
	}


}
