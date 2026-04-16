package com.example.spring_babel_rag;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.domain.io.UserInput;
import com.example.spring_babel_rag.model.AgentLogReport;
import com.example.spring_babel_rag.service.AgentLogAnalysisService;
import org.springframework.stereotype.Component;

@Agent(description = "Skill do analizy skutecznosci agentow na podstawie logow runtime")
@Component
public class AgentLogSkill {

    private final AgentLogAnalysisService analysisService;

    public AgentLogSkill(AgentLogAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @AchievesGoal(description = "powstaje raport porownawczy skutecznosci agentow z rekomendacjami konfiguracji")
    @Action(description = "Przeanalizuj log agentow i utworz raport Markdown z tabelami porownawczymi")
    public AgentLogReport evaluateAgentsFromLog(UserInput userInput) {
        String requestedPath = userInput == null ? null : userInput.getContent();
        return analysisService.analyzeAndWriteReport(requestedPath);
    }
}

