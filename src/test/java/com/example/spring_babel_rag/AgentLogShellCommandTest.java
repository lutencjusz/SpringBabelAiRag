package com.example.spring_babel_rag;

import com.example.spring_babel_rag.configuration.AgentLogSkillProperties;
import com.example.spring_babel_rag.service.AgentLogAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLogShellCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateReportUsingAliasCommand() throws Exception {
        Path logFile = tempDir.resolve("intelijLog.txt");
        Files.write(logFile, List.of(
                "20:37:19.067 [main] INFO Embabel - executing action com.example.spring_babel_rag.BlogWriterAgent.reviewAndImproveBlogDraft",
                "20:37:19.069 [main] WARN ResilientExecutor - Recenzja - blad permanentny (bez retry): Conversion = '\\'",
                "Process finished with exit code 0"
        ));

        AgentLogSkillProperties properties = new AgentLogSkillProperties(logFile.toString(), tempDir.toString(), 10);
        AgentLogAnalysisService analysisService = new AgentLogAnalysisService(properties);
        AgentLogShellCommand command = new AgentLogShellCommand(analysisService);

        String result = command.raportAgentow(logFile.toString());

        assertTrue(result.startsWith("Raport zapisano:"));
        String path = result.substring("Raport zapisano: ".length()).trim();
        assertTrue(Files.exists(Path.of(path)));
    }
}

