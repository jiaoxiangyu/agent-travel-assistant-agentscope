package com.example.travelassistant.persistence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.travelassistant.config.TravelAgentProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TravelStrategyArtifactServiceImplTest {

    @TempDir
    private Path tempDir;

    @Test
    void writeMarkdownSanitizesPathSegmentsAndWritesContent() throws Exception {
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setArtifactDir(tempDir);
        TravelStrategyArtifactServiceImpl service = new TravelStrategyArtifactServiceImpl(properties);

        String artifactPath =
                service.writeMarkdown(
                        "conversation/2026:summer", "alice@example.com", "上海 2 天", "## 行程\n外滩和豫园");

        Path file = Path.of(artifactPath);
        assertThat(file).exists().isRegularFile();
        assertThat(file.getParent()).isEqualTo(tempDir.resolve("alice_example.com").resolve("conversation_2026_summer"));
        assertThat(file.getFileName().toString()).endsWith(".md");

        String markdown = Files.readString(file);
        assertThat(markdown).contains("# 旅行策略");
        assertThat(markdown).contains("- 会话 ID：conversation/2026:summer");
        assertThat(markdown).contains("- 用户 ID：alice@example.com");
        assertThat(markdown).contains("## 用户需求");
        assertThat(markdown).contains("上海 2 天");
        assertThat(markdown).contains("## 行程\n外滩和豫园");
    }

    @Test
    void writeMarkdownUsesUnknownForBlankPathSegments() {
        TravelAgentProperties properties = new TravelAgentProperties();
        properties.setArtifactDir(tempDir);
        TravelStrategyArtifactServiceImpl service = new TravelStrategyArtifactServiceImpl(properties);

        String artifactPath = service.writeMarkdown("", " ", "需求", "回答");

        assertThat(Path.of(artifactPath).getParent()).isEqualTo(tempDir.resolve("unknown").resolve("unknown"));
    }
}
