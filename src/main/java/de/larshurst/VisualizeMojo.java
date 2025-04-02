package de.larshurst;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Mojo(name = "visualize", aggregator = true, requiresProject = true, threadSafe = true, defaultPhase = LifecyclePhase.NONE)
public class VisualizeMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        getLog().info("🔍 Dependency Graph Visualizer: Start");

        Set<String> nodeIds = new HashSet<>();
        List<Map<String, String>> nodes = new ArrayList<>();
        List<Map<String, String>> edges = new ArrayList<>();

        List<MavenProject> modules = project.getCollectedProjects();
        if (modules == null || modules.isEmpty()) {
            modules = List.of(project); // fallback für Single-Module
        }

        for (MavenProject mod : modules) {
            System.out.println(mod.getGroupId() + ":" + mod.getArtifactId() + ":" + mod.getVersion());
            String modId = mod.getGroupId() + ":" + mod.getArtifactId() + ":" + mod.getVersion();
            if (nodeIds.add(modId)) {
                nodes.add(Map.of("id", modId, "label", mod.getArtifactId() + ":" + mod.getVersion(), "module", "true"));
            } else {
                nodes.removeIf(node -> node.get("id").equals(modId));
                nodes.add(Map.of("id", modId, "label", mod.getArtifactId(), "module", "true"));
            }

            List<Dependency> artifacts = mod.getDependencies();
            if (artifacts == null || artifacts.isEmpty()) {
                artifacts = new ArrayList<>();
            }
            for (Dependency artifact : artifacts) {
                String depId = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();

                if (nodeIds.add(depId)) {
                    nodes.add(Map.of("id", depId, "label", artifact.getArtifactId() + ":" + artifact.getVersion(), "module", "false"));
                }

                edges.add(Map.of("from", modId, "to", depId));
            }
        }

        Map<String, Object> graphData = new HashMap<>();
        graphData.put("nodes", nodes);
        graphData.put("edges", edges);

        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(graphData);

            exportStaticHtml(json);
            getLog().info("📄 HTML-Datei erzeugt: dependency-graph.html");
        } catch (Exception e) {
            throw new MojoExecutionException("Error during graph creation", e);
        }
    }

    private void exportStaticHtml(String jsonData) throws IOException {
        InputStream templateStream = getClass().getClassLoader().getResourceAsStream("static/index.html");
        if (templateStream == null) throw new FileNotFoundException("index.html nicht gefunden");

        String htmlTemplate = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
        String injected = htmlTemplate.replace(
                "[] //DATA",
                jsonData + ";"
        );

        String targetPath = project.getBuild().getDirectory() + File.separator + "dependency-graph.html";
        Path output = Path.of(targetPath);

        Files.createDirectories(output.getParent());
        Files.writeString(output, injected, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
