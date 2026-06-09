package dev.shaaf.gbuilder.cli;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

@ApplicationScoped
public class GbuilderVersionProvider implements CommandLine.IVersionProvider {

    @ConfigProperty(name = "gbuilder.version")
    String version;

    @Override
    public String[] getVersion() {
        return new String[] { version };
    }
}
