package com.example.spring_babel_rag.service;

import com.example.spring_babel_rag.configuration.AgentLogSkillProperties;
import com.example.spring_babel_rag.model.AgentLogReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLogAnalysisServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateMarkdownReportWithTablesAndAnomalies() throws Exception {
        Path logFile = tempDir.resolve("intelijLog.txt");
        Files.write(logFile, List.of(
                "20:37:19.067 [main] INFO  Embabel - [suspicious] executing action com.example.spring_babel_rag.BlogWriterAgent.reviewAndImproveBlogDraft",
                "20:37:19.068 [main] INFO  Embabel - executed action com.example.spring_babel_rag.BlogWriterAgent.reviewAndImproveBlogDraft in PT39.716S",
                "entries: {\"stage\":\"Draft\",\"content\":\"# Tytul\\n\\nProsta tresc bez linkow\"}",
                "entries: {\"stage\":\"Reviewed\",\"content\":\"# Tytul\\n\\nRozszerzona tresc z [linkiem](https://example.com)\\n## Sekcja\"}",
                "LLMs used: [gpt-4o, gpt-5-mini]",
                "Calls: 5",
                "Prompt tokens: 12 572",
                "Completion tokens: 19 620",
                "Cost: $0.1120",
                "20:37:19.069 [main] WARN  ResilientExecutor - Recenzja - blad permanentny (bez retry): Conversion = '\\'",
                "20:37:19.070 [main] ERROR BlogWriterAgent - Blad przy recenzji wpisu: Conversion = '\\'",
                "20:37:19.071 [main] INFO  RetryProperties - Operation Action-com.example.spring_babel_rag.BlogWriterAgent.reviewAndImproveBlogDraft: Retry error. Retry count: 1",
                "Process finished with exit code 0"
        ));

        AgentLogSkillProperties properties = new AgentLogSkillProperties(logFile.toString(), tempDir.toString(), 25);
        AgentLogAnalysisService service = new AgentLogAnalysisService(properties);

        AgentLogReport report = service.analyzeAndWriteReport(logFile.toString());

        assertTrue(report.content().startsWith("# Raport: uzytecznosc etapow i przyrost tresci (intelijLog)"));
        assertTrue(report.content().contains("## Checklist wykonania analizy"));
        assertTrue(report.content().contains("## 2) Metryki wykonania (czas, koszt, stabilnosc)"));
        assertTrue(report.content().contains("### Delta miedzy kolejnymi etapami"));
        assertTrue(report.content().contains("Draft -> Reviewed"));
        assertTrue(report.content().contains("## 4) Ranking uzytecznosci etapow"));
        assertTrue(report.content().contains("| 1 |"));
        assertTrue(report.content().contains("## 5) Porownanie etapow"));
        assertTrue(report.content().contains("## 6) Zmiany wprowadzane przez etapy"));
        assertTrue(report.content().contains("## 7) Nieprawidlowosci w pracy etapow"));
        assertTrue(report.content().contains("Prompt tokens | 12 572"));
        assertTrue(report.content().contains("Cost | $0.1120"));
        assertTrue(report.content().contains("BlogWriterAgent"));
        assertTrue(report.content().contains("UnknownFormatConversionException") || report.content().contains("Conversion ="));
        assertTrue(Files.exists(Path.of(report.reportPath())));
    }
}

