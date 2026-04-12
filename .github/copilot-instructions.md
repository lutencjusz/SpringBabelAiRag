# Copilot Instructions for SpringBabelRag

## Language and style
- Odpowiadaj po polsku, chyba ze user wyraźnie poprosi o inny język.
- W kodzie i komentarzach stosuj jasny, krotki styl.
- Nie używaj emoji w kodzie i logach.

## Java and architecture
- Preferuj Java 25 i zgodność ze Spring Boot.
- Zachowuj obecna architekturę agentowa (`@Agent`, `@Action`, `@AchievesGoal`).
- Nie zmieniaj publicznych kontraktów modeli bez wyraźnej potrzeby.

## LLM output and formatting
- Preferuj zwracanie czystego Markdown jako `String`, bez JSON wrappera.
- Wymagaj pierwszej linii jako H1: `# <title>`.
- Przy błędach formatu stosuj fallback prompt lub fallback parser.

## Reliability and costs
- Retry tylko dla błędów przejściowych (timeout, 429, 5xx, chwilowe problemy sieciowe).
- Nie retry dla błędów permanentnych (401/403, błędna konfiguracja modelu, stale błędy walidacji).
- Ograniczaj tokeny: usuwaj bloki kodu z etapów, które ich nie potrzebują (np. linker).

## Tests and validation
- Po zmianach kodu uruchom minimum: kompilacje i testy dotknietych modulow.
- Nie deklaruj sukcesu builda bez realnego uruchomienia komend.

## Safety for repo changes
- Nie nadpisuj ani nie cofaj zmian użytkownika bez prośby.
- Zmiany ograniczaj do najmniejszego potrzebnego zakresu.

