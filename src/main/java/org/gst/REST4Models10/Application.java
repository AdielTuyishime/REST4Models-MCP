package org.gst.REST4Models10;

import java.io.File;

import org.gst.REST4Models10.mcp.PerfUtils;
import org.gst.REST4Models10.repository.RepositoryScanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication

@OpenAPIDefinition(info = @Info(description = "<center> <h1> REST APIs For Model Management</h1></center>"
// SwaggerUI: http://localhost:8080/swagger-ui/index.html
// Api-Docs: http://localhost:8080/api-docs
))
public class Application {
/*
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);

		// RepoScanner.REPO_PATH was defined in the RepoScanner class
		File repository = new File(RepositoryScanner.REPO_PATH);

		RepositoryScanner scanner = new RepositoryScanner();
		scanner.scanRepository(repository);

	}
*/
	/*
	public static void main(String[] args) {
	    var ctx = SpringApplication.run(Application.class, args);

	    boolean isMcp = java.util.Arrays.asList(ctx.getEnvironment().getActiveProfiles()).contains("mcp");
	    if (isMcp) {
	        return; // IMPORTANT: don't print/scan in STDIO MCP mode
	    }

	    File repository = new File(RepositoryScanner.REPO_PATH);
	    RepositoryScanner scanner = new RepositoryScanner();
	    scanner.scanRepository(repository);
	}
	*/
	//private static final Logger log = LoggerFactory.getLogger(PerfUtils.class);
	
	public static void main(String[] args) {
	    SpringApplication.run(Application.class, args);
	    //log.info("REPO_PATH(raw)={}", RepositoryScanner.REPO_PATH);
	    //log.info("REPO_PATH(abs)={}", new File(RepositoryScanner.REPO_PATH).getAbsolutePath());
	}
}
