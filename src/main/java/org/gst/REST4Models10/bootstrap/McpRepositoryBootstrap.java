package org.gst.REST4Models10.bootstrap;

import java.io.File;

import org.gst.REST4Models10.repository.RepositoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("mcp")
@Component
public class McpRepositoryBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpRepositoryBootstrap.class);

    @Override
    public void run(ApplicationArguments args) {
        File repository = new File(RepositoryScanner.REPO_PATH);

        // IMPORTANT: do NOT System.out.println here
        log.info("Bootstrapping repository scan (mcp): {}", repository.getAbsolutePath());

        new RepositoryScanner().scanRepository(repository);
    }
}