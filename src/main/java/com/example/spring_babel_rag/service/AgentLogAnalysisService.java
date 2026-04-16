package com.example.spring_babel_rag.service;

import com.example.spring_babel_rag.configuration.AgentLogSkillProperties;
import com.example.spring_babel_rag.model.AgentLogReport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentLogAnalysisService {

    private static final Log log = LogFactory.getLog(AgentLogAnalysisService.class);
    private static final Pattern ACTION_EXEC_PATTERN = Pattern.compile("executing action\\s+([\\w.$]+)\\.(\\w+)");
    private static final Pattern ACTION_DONE_PATTERN = Pattern.compile("executed action\\s+([\\w.$]+)\\.(\\w+)\\s+in\\s+(PT[\\dHMS.]+)");
    private static final Pattern LOG_LEVEL_PATTERN = Pattern.compile("\\b(INFO|WARN|ERROR)\\b");
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})");
    private static final Pattern MODELS_PATTERN = Pattern.compile("LLMs used:\\s*\\[(.+)]");
    private static final Pattern CALLS_PATTERN = Pattern.compile("Calls:\\s*([0-9]+)");
    private static final Pattern PROMPT_TOKENS_PATTERN = Pattern.compile("Prompt tokens:\\s*([0-9\\s,]+)");
    private static final Pattern COMPLETION_TOKENS_PATTERN = Pattern.compile("Completion tokens:\\s*([0-9\\s,]+)");
    private static final Pattern COST_PATTERN = Pattern.compile("Cost:\\s*\\$([0-9.]+)");
    private static final Pattern ENTRY_STAGE_PATTERN = Pattern.compile("\"stage\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ENTRY_CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
    private static final Pattern REVIEWED_CONTENT_PATTERN = Pattern.compile("ReviewedPost\\[.*?content=(.*?), feedback=.*]", Pattern.DOTALL);
    private static final Pattern LINKED_CONTENT_PATTERN = Pattern.compile("LinkedPost\\[.*?content=(.*?), feedback=.*]", Pattern.DOTALL);
    private static final Pattern EDITED_CONTENT_PATTERN = Pattern.compile("EditedPost\\[.*?content=(.*?), feedback=.*]", Pattern.DOTALL);
    private static final Pattern MARKDOWN_CONTENT_PATTERN = Pattern.compile("MarkdownPost\\[.*?content=(.*?), feedback=.*]", Pattern.DOTALL);

    private final AgentLogSkillProperties properties;

    public AgentLogAnalysisService(AgentLogSkillProperties properties) {
        this.properties = properties;
    }

    public AgentLogReport analyzeAndWriteReport(String requestedLogPath) {
        Path logPath = resolveLogPath(requestedLogPath);
        List<String> lines = readLogLines(logPath);
        AnalysisContext ctx = analyze(lines);
        String markdown = toMarkdown(ctx, logPath);
        Path reportPath = writeReport(markdown);
        return new AgentLogReport("Raport: uzytecznosc etapow i przyrost tresci", markdown, reportPath.toAbsolutePath().toString());
    }

    private Path resolveLogPath(String requestedLogPath) {
        if (requestedLogPath != null && !requestedLogPath.isBlank()) {
            return Path.of(requestedLogPath.trim());
        }
        return Path.of(properties.sourceLogPath());
    }

    private List<String> readLogLines(Path logPath) {
        try {
            return Files.readAllLines(logPath);
        } catch (IOException e) {
            throw new IllegalStateException("Nie mozna odczytac logu: " + logPath.toAbsolutePath(), e);
        }
    }

    private Path writeReport(String markdown) {
        Path outputDir = Path.of(properties.outputDir());
        String fileName = "agent-usability-report-intelijlog-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
                + ".md";
        Path path = outputDir.resolve(fileName);
        try {
            Files.createDirectories(outputDir);
            Files.writeString(path, markdown);
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("Nie mozna zapisac raportu: " + path.toAbsolutePath(), e);
        }
    }

    AnalysisContext analyze(List<String> lines) {
        Map<String, ActionStats> actions = new LinkedHashMap<>();
        Map<String, AgentStats> agents = new LinkedHashMap<>();
        List<ActionStats> orderedStages = new ArrayList<>();
        Map<String, ContentSnapshot> snapshotsByStage = new LinkedHashMap<>();
        List<Anomaly> anomalies = new ArrayList<>();
        String currentActionKey = null;
        String exitCode = "brak";
        RunMetrics runMetrics = new RunMetrics();

        for (String line : lines) {
            Matcher execMatcher = ACTION_EXEC_PATTERN.matcher(line);
            if (execMatcher.find()) {
                String className = execMatcher.group(1);
                String method = execMatcher.group(2);
                String actionKey = className + "." + method;
                currentActionKey = actionKey;

                ActionStats stats = actions.computeIfAbsent(actionKey, k -> new ActionStats(simpleName(className), method));
                stats.executions++;
                if (!orderedStages.contains(stats)) {
                    orderedStages.add(stats);
                }
                continue;
            }

            Matcher doneMatcher = ACTION_DONE_PATTERN.matcher(line);
            if (doneMatcher.find()) {
                String className = doneMatcher.group(1);
                String method = doneMatcher.group(2);
                String actionKey = className + "." + method;
                ActionStats stats = actions.computeIfAbsent(actionKey, k -> new ActionStats(simpleName(className), method));
                stats.duration = parseDuration(doneMatcher.group(3));
                if (!orderedStages.contains(stats)) {
                    orderedStages.add(stats);
                }
                continue;
            }

            parseRunMetrics(line, runMetrics);
            parseContentSnapshot(line, snapshotsByStage);

            if (line.contains("Process finished with exit code")) {
                int idx = line.lastIndexOf(' ');
                if (idx > 0 && idx + 1 < line.length()) {
                    exitCode = line.substring(idx + 1).trim();
                }
            }

            String level = parseLevel(line);
            if (level == null) {
                continue;
            }

            ActionStats scoped = currentActionKey == null ? null : actions.get(currentActionKey);
            if (line.contains("Retry error") || line.contains("blad przejsciowy") || line.contains("retry za")) {
                if (scoped != null) {
                    scoped.retries++;
                }
                anomalies.add(new Anomaly(parseTime(line), level, safeAction(scoped), "Wykryto retry po bledzie przejsciowym"));
            }
            if (line.toLowerCase(Locale.ROOT).contains("fallback")) {
                if (scoped != null) {
                    scoped.fallbacks++;
                }
            }
            if (line.contains("UnknownFormatConversionException") || line.contains("Conversion =") || line.contains("blad formatu")) {
                if (scoped != null) {
                    scoped.formatErrors++;
                }
                anomalies.add(new Anomaly(parseTime(line), level, safeAction(scoped), trimMessage(line)));
            }
            if (line.contains("blad permanentny") || line.contains("ERROR")) {
                if (scoped != null) {
                    scoped.permanentErrors++;
                }
                anomalies.add(new Anomaly(parseTime(line), level, safeAction(scoped), trimMessage(line)));
            }
            if (line.contains("Thread interrupted while sleeping") || line.contains("watek przerwany")) {
                if (scoped != null) {
                    scoped.interruptions++;
                }
                anomalies.add(new Anomaly(parseTime(line), level, safeAction(scoped), "Przerwanie watku podczas oczekiwania"));
            }
        }

        for (ActionStats action : actions.values()) {
            AgentStats agent = agents.computeIfAbsent(action.agentName, AgentStats::new);
            agent.executions += action.executions;
            agent.retries += action.retries;
            agent.fallbacks += action.fallbacks;
            agent.permanentErrors += action.permanentErrors;
            agent.formatErrors += action.formatErrors;
            agent.interruptions += action.interruptions;
            if (action.duration != null) {
                agent.totalDuration = agent.totalDuration.plus(action.duration);
            }
            agent.changeTypes.put(classifyChangeType(action.actionName),
                    agent.changeTypes.getOrDefault(classifyChangeType(action.actionName), 0) + action.executions);
        }

        Duration totalDuration = Duration.ZERO;
        for (ActionStats action : orderedStages) {
            if (action.duration != null) {
                totalDuration = totalDuration.plus(action.duration);
            }
        }
        runMetrics.totalDuration = totalDuration;

        List<ContentSnapshot> snapshots = new ArrayList<>(snapshotsByStage.values());
        return new AnalysisContext(agents, orderedStages, snapshots, anomalies, runMetrics, exitCode, lines.size());
    }

    private String toMarkdown(AnalysisContext ctx, Path sourceLogPath) {
        StringBuilder md = new StringBuilder();
        md.append("# Raport: uzytecznosc etapow i przyrost tresci (intelijLog)\n\n");
        md.append("## Zakres i zrodla\n");
        md.append("- Zrodlo logu: `").append(sourceLogPath.toAbsolutePath()).append("`\n");
        md.append("- Liczba linii: ").append(ctx.lineCount).append("\n");
        md.append("- Kod wyjscia procesu: ").append(ctx.exitCode).append("\n");
        md.append("- Raport wygenerowany przez skill: `AgentLogSkill`\n\n");

        md.append("## Checklist wykonania analizy\n");
        md.append("- [x] Odtworzenie sekwencji etapow z logu\n");
        md.append("- [x] Ocena skutecznosci i stabilnosci wykonania\n");
        md.append("- [x] Wykrywanie nieprawidlowosci i anomalii\n");
        md.append("- [x] Sugestie konfiguracji i doboru modeli\n\n");

        md.append("## 1) Faktycznie uruchomione etapy\n");
        md.append("| # | Komponent | Etap | Czas | Ocena etapu |\n");
        md.append("|---:|---|---|---|---|\n");
        if (ctx.orderedStages.isEmpty()) {
            md.append("| 1 | - | Brak wykrytych etapow wykonania | - | brak danych |\n");
        } else {
            int stageIndex = 1;
            for (ActionStats action : ctx.orderedStages) {
                md.append("| ").append(stageIndex++)
                        .append(" | ").append(action.agentName)
                        .append(" | ").append(action.actionName)
                        .append(" | ").append(formatDuration(action.duration))
                        .append(" | ").append(stageUsefulnessLabel(action))
                        .append(" |\n");
            }
        }
        md.append("\n");

        md.append("## 2) Metryki wykonania (czas, koszt, stabilnosc)\n\n");
        md.append("### Czas per etap\n");
        md.append("| Etap | Czas |\n");
        md.append("|---|---|\n");
        for (ActionStats action : ctx.orderedStages) {
            md.append("| ").append(action.actionName)
                    .append(" | ").append(formatDuration(action.duration))
                    .append(" |\n");
        }
        md.append("| Czas calkowity pipeline | ").append(formatDuration(ctx.runMetrics.totalDuration)).append(" |\n\n");

        md.append("### Uzycie modeli i koszt\n");
        md.append("| Metryka | Wartosc |\n");
        md.append("|---|---|\n");
        md.append("| LLMs | ").append(ctx.runMetrics.modelsUsed.isEmpty() ? "brak danych" : String.join(", ", ctx.runMetrics.modelsUsed)).append(" |\n");
        md.append("| Calls | ").append(ctx.runMetrics.calls != null ? ctx.runMetrics.calls : "brak danych").append(" |\n");
        md.append("| Prompt tokens | ").append(ctx.runMetrics.promptTokens != null ? formatNumber(ctx.runMetrics.promptTokens) : "brak danych").append(" |\n");
        md.append("| Completion tokens | ").append(ctx.runMetrics.completionTokens != null ? formatNumber(ctx.runMetrics.completionTokens) : "brak danych").append(" |\n");
        md.append("| Cost | ").append(ctx.runMetrics.costUsd != null ? "$" + String.format(Locale.ROOT, "%.4f", ctx.runMetrics.costUsd) : "brak danych").append(" |\n\n");

        md.append("### Stabilnosc\n");
        md.append("- Retry: **").append(totalRetries(ctx)).append("**\n");
        md.append("- Bledy formatu: **").append(totalFormatErrors(ctx)).append("**\n");
        md.append("- Bledy permanentne: **").append(totalPermanentErrors(ctx)).append("**\n");
        md.append("- Przerwania watkow: **").append(totalInterruptions(ctx)).append("**\n\n");

        md.append("## 3) Przyrost tresci i struktury miedzy etapami\n\n");
        if (ctx.snapshots.size() >= 2) {
            md.append("Metryki policzone na podstawie snapshotow tresci wykrytych w logu.\n\n");
            md.append("| Etap | Znaki | Slowa | Linki | `##` | `###` | Bloki ``` |\n");
            md.append("|---|---:|---:|---:|---:|---:|---:|\n");
            for (ContentSnapshot snapshot : ctx.snapshots) {
                md.append("| ").append(snapshot.stageLabel)
                        .append(" | ").append(formatNumber(snapshot.stats.characters))
                        .append(" | ").append(formatNumber(snapshot.stats.words))
                        .append(" | ").append(snapshot.stats.links)
                        .append(" | ").append(snapshot.stats.h2)
                        .append(" | ").append(snapshot.stats.h3)
                        .append(" | ").append(snapshot.stats.codeBlocks)
                        .append(" |\n");
            }
            md.append("\n### Delta miedzy kolejnymi etapami\n");
            for (int i = 1; i < ctx.snapshots.size(); i++) {
                ContentSnapshot prev = ctx.snapshots.get(i - 1);
                ContentSnapshot current = ctx.snapshots.get(i);
                int charsDelta = current.stats.characters - prev.stats.characters;
                int wordsDelta = current.stats.words - prev.stats.words;
                md.append("- ").append(prev.stageLabel).append(" -> ").append(current.stageLabel)
                        .append(": **").append(formatSigned(charsDelta)).append(" znakow**, **")
                        .append(formatSigned(wordsDelta)).append(" slow**\n");
            }
        } else {
            md.append("| Etap | Wklad merytoryczny | Zmiany redakcyjne | Ryzyko kosztowe |\n");
            md.append("|---|---|---|---|\n");
            for (ActionStats action : ctx.orderedStages) {
                md.append("| ").append(action.actionName)
                        .append(" | ").append(stageContribution(action.actionName))
                        .append(" | ").append(stageEditorialChange(action.actionName))
                        .append(" | ").append(stageCostRisk(action))
                        .append(" |\n");
            }
            md.append("\n_Delta liczbowe nie sa dostepne, bo log nie zawiera kompletnych snapshotow tresci etapow._\n");
        }
        md.append("\n");

        md.append("## 4) Ranking uzytecznosci etapow\n\n");
        md.append("| Miejsce | Etap | Ocena | Uzasadnienie |\n");
        md.append("|---:|---|---:|---|\n");
        List<ActionStats> rankedStages = rankStages(ctx.orderedStages);
        int place = 1;
        for (ActionStats stage : rankedStages) {
            md.append("| ").append(place++)
                    .append(" | ").append(stage.actionName)
                    .append(" | ").append(String.format(Locale.ROOT, "%.1f", stageRankScore(stage)))
                    .append(" | ").append(stageRankReason(stage))
                    .append(" |\n");
        }
        if (rankedStages.isEmpty()) {
            md.append("| 1 | brak danych | 0.0 | Brak wykrytych etapow w logu. |\n");
        }
        md.append("\n");

        md.append("## 5) Porownanie etapow\n\n");
        md.append("| Etap | Uruchomienia | Retry | Fallback | Bledy permanentne | Bledy formatu | Przerwania | Skutecznosc |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        List<ActionStats> sortedStages = new ArrayList<>(ctx.orderedStages);
        sortedStages.sort(Comparator.comparingDouble(this::stageEffectiveness).reversed());
        for (ActionStats stage : sortedStages) {
            md.append("| ").append(stage.actionName)
                    .append(" | ").append(stage.executions)
                    .append(" | ").append(stage.retries)
                    .append(" | ").append(stage.fallbacks)
                    .append(" | ").append(stage.permanentErrors)
                    .append(" | ").append(stage.formatErrors)
                    .append(" | ").append(stage.interruptions)
                    .append(" | ").append(String.format(Locale.ROOT, "%.1f%%", stageEffectiveness(stage) * 100.0))
                    .append(" |\n");
        }
        md.append("\n");

        List<AgentStats> sortedAgents = new ArrayList<>(ctx.agents.values());
        sortedAgents.sort(Comparator.comparingDouble(this::effectiveness).reversed());

        md.append("## 6) Zmiany wprowadzane przez etapy\n\n");
        md.append("| Etap | Typ zmiany | Liczba operacji |\n");
        md.append("|---|---|---:|\n");
        int changeRows = 0;
        for (ActionStats stage : sortedStages) {
            if (changeRows >= properties.maxTableRows()) {
                break;
            }
            md.append("| ").append(stage.actionName)
                    .append(" | ").append(classifyChangeType(stage.actionName))
                    .append(" | ").append(stage.executions)
                    .append(" |\n");
            changeRows++;
        }
        md.append("\n");

        md.append("## 7) Nieprawidlowosci w pracy etapow\n\n");
        md.append("| Czas | Poziom | Kontekst | Opis |\n");
        md.append("|---|---|---|---|\n");
        int anomalyRows = 0;
        for (Anomaly anomaly : ctx.anomalies) {
            if (anomalyRows >= properties.maxTableRows()) {
                break;
            }
            md.append("| ").append(escapeCell(anomaly.time))
                    .append(" | ").append(escapeCell(anomaly.level))
                    .append(" | ").append(escapeCell(anomaly.scope))
                    .append(" | ").append(escapeCell(anomaly.message))
                    .append(" |\n");
            anomalyRows++;
        }
        if (anomalyRows == 0) {
            md.append("| - | - | - | Nie wykryto nieprawidlowosci o podwyzszonym ryzyku. |\n");
        }
        md.append("\n");

        md.append("## 8) Rekomendacje konfiguracji i modeli\n\n");
        for (String suggestion : buildSuggestions(ctx, sortedAgents)) {
            md.append("1. ").append(suggestion).append("\n");
        }

        md.append("\n## 9) Podsumowanie koncowe\n\n");
        md.append("Pipeline uruchomil **").append(ctx.orderedStages.size()).append("** etapow, a srednia skutecznosc etapow wyniosla **")
                .append(String.format(Locale.ROOT, "%.1f%%", averageEffectiveness(sortedAgents) * 100.0)).append("**. ");
        if (ctx.anomalies.isEmpty()) {
            md.append("Nie wykryto krytycznych nieprawidlowosci wymagajacych natychmiastowej interwencji.");
        } else {
            md.append("Wykryto **").append(ctx.anomalies.size()).append("** sygnalow nieprawidlowosci, ktore warto zweryfikowac przed kolejnym uruchomieniem.");
        }
        md.append("\n");

        return md.toString();
    }

    private List<String> buildSuggestions(AnalysisContext ctx, List<AgentStats> sortedAgents) {
        List<String> suggestions = new ArrayList<>();

        long formatErrors = sortedAgents.stream().mapToLong(a -> a.formatErrors).sum();
        if (formatErrors > 0) {
            suggestions.add("Dla akcji z bledami formatu wlacz dodatkowy fallback parser i walidacje H1 przed zapisem odpowiedzi modelu.");
        }

        long retryCount = sortedAgents.stream().mapToLong(a -> a.retries).sum();
        if (retryCount > 0) {
            suggestions.add("Podnies obserwowalnosc timeoutow i rozwaz zwiekszenie `retry-policy.initial-delay-ms` dla stabilniejszego backoff przy przeciazeniu.");
        }

        long permanentErrors = sortedAgents.stream().mapToLong(a -> a.permanentErrors).sum();
        if (permanentErrors > 0) {
            suggestions.add("Wylacz retry dla bledow permanentnych (401/403/400 limit konta) i dodaj szybkie przelaczenie na model zapasowy w konfiguracji roli.");
        }

        if (ctx.exitCode.equals("0") && ctx.anomalies.stream().anyMatch(a -> "ERROR".equals(a.level))) {
            suggestions.add("Proces zakonczyl sie kodem 0 mimo bledow ERROR. Dodaj warunek fail-fast lub metryke, ktora oznaczy przebieg jako nieudany.");
        }

        for (AgentStats agent : sortedAgents) {
            if (agent.executions == 0) {
                continue;
            }
            if (effectiveness(agent) < 0.70) {
                suggestions.add("Etap powiazany z komponentem `" + agent.agentName + "` ma niska skutecznosc; rozwaz zmiane modelu na stabilniejszy dla zadan wymagajacych poprawnego formatu Markdown.");
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Brak krytycznych odchylen. Utrzymaj obecny podzial modeli i monitoruj trend retry oraz fallbackow.");
        }
        return suggestions;
    }

    private String classifyChangeType(String actionName) {
        String lower = actionName.toLowerCase(Locale.ROOT);
        if (lower.contains("write") || lower.contains("draft")) {
            return "Tworzenie bazowej tresci";
        }
        if (lower.contains("review") || lower.contains("improve")) {
            return "Recenzja techniczna i korekty merytoryczne";
        }
        if (lower.contains("link")) {
            return "Dodawanie linkow referencyjnych";
        }
        if (lower.contains("edit")) {
            return "Korekta jezykowa i redakcyjna";
        }
        if (lower.contains("attractive") || lower.contains("markdown")) {
            return "Poprawa struktury i czytelnosci Markdown";
        }
        return "Modyfikacja wg akcji: " + actionName;
    }

    private String stageUsefulnessLabel(ActionStats action) {
        String lower = action.actionName.toLowerCase(Locale.ROOT);
        if (lower.contains("link")) {
            return "bardzo wysoka";
        }
        if (lower.contains("review") || lower.contains("write")) {
            return "wysoka";
        }
        if (lower.contains("edit") || lower.contains("attractive") || lower.contains("markdown")) {
            return "srednio-wysoka";
        }
        return "srednia";
    }

    private String stageContribution(String actionName) {
        String lower = actionName.toLowerCase(Locale.ROOT);
        if (lower.contains("write")) {
            return "Tworzy szkic bazowy i glowny trzon tresci";
        }
        if (lower.contains("review")) {
            return "Doprecyzowuje merytoryke i poprawia spojnosc";
        }
        if (lower.contains("link")) {
            return "Podnosi wiarygodnosc przez linki referencyjne";
        }
        if (lower.contains("edit")) {
            return "Wygladza jezyk i porzadkuje strukture";
        }
        if (lower.contains("attractive") || lower.contains("markdown")) {
            return "Podnosi czytelnosc i publikowalnosc dokumentu";
        }
        return "Modyfikacja wg celu etapu";
    }

    private String stageEditorialChange(String actionName) {
        String lower = actionName.toLowerCase(Locale.ROOT);
        if (lower.contains("write")) {
            return "Szkielet i podstawowe sekcje";
        }
        if (lower.contains("review")) {
            return "Korekty techniczne i logiczne";
        }
        if (lower.contains("link")) {
            return "Terminologia i odsylacze";
        }
        if (lower.contains("edit")) {
            return "Korekta jezykowa i styl";
        }
        if (lower.contains("attractive") || lower.contains("markdown")) {
            return "Formatowanie i UX czytania";
        }
        return "Drobne poprawki";
    }

    private String stageCostRisk(ActionStats action) {
        if (action.duration == null) {
            return "nieznane";
        }
        long seconds = action.duration.toSeconds();
        if (seconds >= 45) {
            return "wysokie";
        }
        if (seconds >= 25) {
            return "srednie";
        }
        return "niskie";
    }

    private List<ActionStats> rankStages(List<ActionStats> stages) {
        List<ActionStats> ranked = new ArrayList<>(stages);
        ranked.sort(Comparator.comparingDouble(this::stageRankScore).reversed());
        return ranked;
    }

    private double stageRankScore(ActionStats stage) {
        double base = stageBaseScore(stage.actionName);
        double penalty = stage.retries * 0.4 + stage.permanentErrors * 1.0 + stage.formatErrors * 0.8 + stage.interruptions * 0.6;
        return Math.max(0.0, base - penalty);
    }

    private double stageBaseScore(String actionName) {
        String lower = actionName.toLowerCase(Locale.ROOT);
        if (lower.contains("link")) {
            return 9.5;
        }
        if (lower.contains("review")) {
            return 9.0;
        }
        if (lower.contains("write")) {
            return 8.7;
        }
        if (lower.contains("attractive") || lower.contains("markdown")) {
            return 8.2;
        }
        if (lower.contains("edit")) {
            return 7.9;
        }
        return 7.0;
    }

    private String stageRankReason(ActionStats stage) {
        String lower = stage.actionName.toLowerCase(Locale.ROOT);
        if (lower.contains("link")) {
            return "Najwiekszy wplyw na wiarygodnosc techniczna i jakosc zrodel.";
        }
        if (lower.contains("review")) {
            return "Silny wplyw merytoryczny i korekta tresci.";
        }
        if (lower.contains("write")) {
            return "Buduje trzon artykulu i material wyjsciowy dla kolejnych etapow.";
        }
        if (lower.contains("attractive") || lower.contains("markdown")) {
            return "Podnosi publikowalnosc i UX czytania finalnej wersji.";
        }
        if (lower.contains("edit")) {
            return "Poprawia styl i czytelnosc, zwykle z mniejszym przyrostem merytoryki.";
        }
        return "Etap pomocniczy w pipeline.";
    }

    private double effectiveness(AgentStats agent) {
        if (agent.executions == 0) {
            return 1.0;
        }
        double penalty = agent.permanentErrors + agent.formatErrors + (0.25 * agent.retries) + (0.25 * agent.fallbacks);
        return Math.max(0.0, (agent.executions - penalty) / agent.executions);
    }

    private double stageEffectiveness(ActionStats stage) {
        if (stage.executions == 0) {
            return 1.0;
        }
        double penalty = stage.permanentErrors + stage.formatErrors + (0.25 * stage.retries) + (0.25 * stage.fallbacks);
        return Math.max(0.0, (stage.executions - penalty) / stage.executions);
    }

    private String parseLevel(String line) {
        Matcher m = LOG_LEVEL_PATTERN.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    private void parseRunMetrics(String line, RunMetrics metrics) {
        Matcher modelsMatcher = MODELS_PATTERN.matcher(line);
        if (modelsMatcher.find()) {
            String[] models = modelsMatcher.group(1).split(",");
            for (String model : models) {
                String trimmed = model.trim();
                if (!trimmed.isBlank() && !metrics.modelsUsed.contains(trimmed)) {
                    metrics.modelsUsed.add(trimmed);
                }
            }
        }

        Matcher callsMatcher = CALLS_PATTERN.matcher(line);
        if (callsMatcher.find()) {
            metrics.calls = Integer.parseInt(callsMatcher.group(1));
        }

        Matcher promptMatcher = PROMPT_TOKENS_PATTERN.matcher(line);
        if (promptMatcher.find()) {
            metrics.promptTokens = parseIntegerValue(promptMatcher.group(1));
        }

        Matcher completionMatcher = COMPLETION_TOKENS_PATTERN.matcher(line);
        if (completionMatcher.find()) {
            metrics.completionTokens = parseIntegerValue(completionMatcher.group(1));
        }

        Matcher costMatcher = COST_PATTERN.matcher(line);
        if (costMatcher.find()) {
            metrics.costUsd = Double.parseDouble(costMatcher.group(1));
        }
    }

    private void parseContentSnapshot(String line, Map<String, ContentSnapshot> snapshotsByStage) {
        Matcher entryStageMatcher = ENTRY_STAGE_PATTERN.matcher(line);
        Matcher entryContentMatcher = ENTRY_CONTENT_PATTERN.matcher(line);
        if (entryStageMatcher.find() && entryContentMatcher.find()) {
            String stage = normalizeStageLabel(entryStageMatcher.group(1));
            String content = unescapeLogText(entryContentMatcher.group(1));
            snapshotsByStage.put(stage, new ContentSnapshot(stage, computeContentStats(content)));
            return;
        }

        addSnapshotByRecordPattern(snapshotsByStage, "Reviewed", REVIEWED_CONTENT_PATTERN, line);
        addSnapshotByRecordPattern(snapshotsByStage, "Linked", LINKED_CONTENT_PATTERN, line);
        addSnapshotByRecordPattern(snapshotsByStage, "Edited", EDITED_CONTENT_PATTERN, line);
        addSnapshotByRecordPattern(snapshotsByStage, "Final (Markdown)", MARKDOWN_CONTENT_PATTERN, line);
    }

    private void addSnapshotByRecordPattern(Map<String, ContentSnapshot> snapshotsByStage, String stage, Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String content = unescapeLogText(matcher.group(1));
            snapshotsByStage.put(stage, new ContentSnapshot(stage, computeContentStats(content)));
        }
    }

    private ContentStats computeContentStats(String content) {
        if (content == null) {
            return new ContentStats(0, 0, 0, 0, 0, 0);
        }
        String normalized = content.trim();
        int characters = normalized.length();
        int words = normalized.isBlank() ? 0 : normalized.split("\\s+").length;
        int links = countMatches(normalized, Pattern.compile("\\[[^\\n]+]\\([^\\n]+\\)"));
        int h2 = countMatches(normalized, Pattern.compile("(?m)^##\\s+"));
        int h3 = countMatches(normalized, Pattern.compile("(?m)^###\\s+"));
        int codeBlocks = countMatches(normalized, Pattern.compile("(?m)^```") ) / 2;
        return new ContentStats(characters, words, links, h2, h3, Math.max(0, codeBlocks));
    }

    private int countMatches(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String normalizeStageLabel(String rawStage) {
        String lower = rawStage.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("draft")) {
            return "Draft";
        }
        if (lower.contains("review")) {
            return "Reviewed";
        }
        if (lower.contains("link")) {
            return "Linked";
        }
        if (lower.contains("edit")) {
            return "Edited";
        }
        if (lower.contains("final") || lower.contains("markdown") || lower.contains("attractive")) {
            return "Final (Markdown)";
        }
        return rawStage;
    }

    private String unescapeLogText(String content) {
        return content
                .replace("\\\\n", "\n")
                .replace("\\\\r", "\r")
                .replace("\\\\\"", "\"")
                .replace("\\\\\\\\", "\\");
    }

    private Duration parseDuration(String value) {
        try {
            return Duration.parse(value);
        } catch (Exception e) {
            log.debug("Nie udalo sie sparsowac czasu etapu: " + value, e);
            return null;
        }
    }

    private String parseTime(String line) {
        Matcher m = TIME_PATTERN.matcher(line);
        return m.find() ? m.group(1) : "-";
    }

    private String trimMessage(String line) {
        String compact = line.trim();
        if (compact.length() <= 140) {
            return compact;
        }
        return compact.substring(0, 140) + "...";
    }

    private String escapeCell(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "brak danych";
        }
        return duration.toString();
    }

    private String formatNumber(Integer value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private String formatSigned(int value) {
        if (value > 0) {
            return "+" + formatNumber(value);
        }
        return formatNumber(value);
    }

    private int parseIntegerValue(String value) {
        String normalized = value.replace(" ", "").replace(",", "");
        return Integer.parseInt(normalized);
    }

    private int totalRetries(AnalysisContext ctx) {
        return ctx.agents.values().stream().mapToInt(a -> a.retries).sum();
    }

    private int totalFormatErrors(AnalysisContext ctx) {
        return ctx.agents.values().stream().mapToInt(a -> a.formatErrors).sum();
    }

    private int totalPermanentErrors(AnalysisContext ctx) {
        return ctx.agents.values().stream().mapToInt(a -> a.permanentErrors).sum();
    }

    private int totalInterruptions(AnalysisContext ctx) {
        return ctx.agents.values().stream().mapToInt(a -> a.interruptions).sum();
    }

    private double averageEffectiveness(List<AgentStats> sortedAgents) {
        if (sortedAgents.isEmpty()) {
            return 1.0;
        }
        double sum = 0;
        for (AgentStats agent : sortedAgents) {
            sum += effectiveness(agent);
        }
        return sum / sortedAgents.size();
    }

    private String simpleName(String className) {
        int idx = className.lastIndexOf('.');
        if (idx < 0 || idx + 1 >= className.length()) {
            return className;
        }
        return className.substring(idx + 1);
    }

    private String safeAction(ActionStats stats) {
        return stats == null ? "niezidentyfikowany" : stats.agentName + "." + stats.actionName;
    }

    static class AnalysisContext {
        private final Map<String, AgentStats> agents;
        private final List<ActionStats> orderedStages;
        private final List<ContentSnapshot> snapshots;
        private final List<Anomaly> anomalies;
        private final RunMetrics runMetrics;
        private final String exitCode;
        private final int lineCount;

        AnalysisContext(Map<String, AgentStats> agents,
                        List<ActionStats> orderedStages,
                        List<ContentSnapshot> snapshots,
                        List<Anomaly> anomalies,
                        RunMetrics runMetrics,
                        String exitCode,
                        int lineCount) {
            this.agents = agents;
            this.orderedStages = orderedStages;
            this.snapshots = snapshots;
            this.anomalies = anomalies;
            this.runMetrics = runMetrics;
            this.exitCode = exitCode;
            this.lineCount = lineCount;
        }
    }

    static class ActionStats {
        private final String agentName;
        private final String actionName;
        private Duration duration;
        private int executions;
        private int retries;
        private int fallbacks;
        private int permanentErrors;
        private int formatErrors;
        private int interruptions;

        ActionStats(String agentName, String actionName) {
            this.agentName = agentName;
            this.actionName = actionName;
        }
    }

    static class AgentStats {
        private final String agentName;
        private Duration totalDuration = Duration.ZERO;
        private int executions;
        private int retries;
        private int fallbacks;
        private int permanentErrors;
        private int formatErrors;
        private int interruptions;
        private final Map<String, Integer> changeTypes = new HashMap<>();

        AgentStats(String agentName) {
            this.agentName = agentName;
        }
    }

    static class RunMetrics {
        private final List<String> modelsUsed = new ArrayList<>();
        private Integer calls;
        private Integer promptTokens;
        private Integer completionTokens;
        private Double costUsd;
        private Duration totalDuration = Duration.ZERO;
    }

    record ContentSnapshot(String stageLabel, ContentStats stats) {}

    record ContentStats(int characters, int words, int links, int h2, int h3, int codeBlocks) {}

    record Anomaly(String time, String level, String scope, String message) {}
}

