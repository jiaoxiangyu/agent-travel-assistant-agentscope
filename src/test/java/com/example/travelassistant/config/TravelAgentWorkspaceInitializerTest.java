package com.example.travelassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TravelAgentWorkspaceInitializerTest {

    @TempDir
    private Path tempDir;

    @Test
    void initializeCreatesLocalWorkspaceSeedFiles() throws Exception {
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setWorkspaceDir(tempDir.resolve("workspace"));

        new TravelAgentWorkspaceInitializer(properties).initialize();

        assertThat(properties.getWorkspaceDir().resolve("AGENTS.md")).exists().isRegularFile();
        assertThat(properties.getWorkspaceDir().resolve("knowledge").resolve("KNOWLEDGE.md"))
                .exists()
                .isRegularFile();
        assertThat(properties.getWorkspaceDir().resolve("skills")).exists().isDirectory();
        assertThat(
                        properties
                                .getWorkspaceDir()
                                .resolve("skills")
                                .resolve("weather")
                                .resolve("SKILL.md"))
                .exists()
                .isRegularFile();
        assertThat(
                        properties
                                .getWorkspaceDir()
                                .resolve("skills")
                                .resolve("weather")
                                .resolve("scripts")
                                .resolve("get_weather.py"))
                .exists()
                .isRegularFile();
        assertThat(properties.getWorkspaceDir().resolve("subagents")).exists().isDirectory();
        assertThat(properties.getWorkspaceDir().resolve("plans")).exists().isDirectory();
        assertThat(Files.readString(properties.getWorkspaceDir().resolve("AGENTS.md")))
                .contains("中文旅行规划助手")
                .contains("最终回答请用中文 Markdown");
        assertThat(
                        Files.readString(
                                properties
                                        .getWorkspaceDir()
                                        .resolve("skills")
                                        .resolve("weather")
                                        .resolve("SKILL.md")))
                .contains("name: weather")
                .contains("scripts/get_weather.py");
    }

    @Test
    void initializeDoesNotOverwriteExistingAgentsFile() throws Exception {
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setWorkspaceDir(tempDir.resolve("workspace"));
        Files.createDirectories(properties.getWorkspaceDir());
        Files.writeString(properties.getWorkspaceDir().resolve("AGENTS.md"), "custom rules");

        new TravelAgentWorkspaceInitializer(properties).initialize();

        assertThat(Files.readString(properties.getWorkspaceDir().resolve("AGENTS.md")))
                .isEqualTo("custom rules");
    }
}
