package com.example.spring_babel_rag;

import com.example.spring_babel_rag.model.AgentLogReport;
import com.example.spring_babel_rag.service.AgentLogAnalysisService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class AgentLogShellCommand {

    private final AgentLogAnalysisService analysisService;

    public AgentLogShellCommand(AgentLogAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @ShellMethod(key = "raport-agentów", value = "Generuje raport skuteczności agentów na podstawie logu runtime")
    public String raportAgentow(@ShellOption(defaultValue = ShellOption.NULL, help = "Opcjonalna ścieżka do pliku logu") String logPath) {
        String normalizedPath = (logPath == null || logPath.isBlank()) ? null : logPath.trim();
        AgentLogReport report = analysisService.analyzeAndWriteReport(normalizedPath);
        return "Raport zapisano: " + report.reportPath();
    }
}

