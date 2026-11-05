package org.gst.REST4Models10.bootstrap;

import java.io.File;

import org.gst.REST4Models10.repository.RepositoryScanner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!mcp")
@Component
public class RestRepositoryBootstrap implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        File repository = new File(RepositoryScanner.REPO_PATH);
        new RepositoryScanner().scanRepository(repository);
    }
}
