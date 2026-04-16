<div align="right">EN: <a href="README.md">README.md</a></div>

# SpringBabelAiRag

Prosty projekt `Spring Boot` + `Embabel` do generowania wpisów blogowych przy pomocy modeli OpenAI.

Aplikacja:
- przyjmuje pytanie lub temat,
- tłumaczy wejście na angielski,
- tworzy szkic wpisu,
- poprawia technicznie treść,
- tłumaczy wynik na polski,
- zapisuje gotowy wpis do katalogu `blog-posts/`.

## Wymagania

- Java 25
- Git
- dostęp do OpenAI API

Projekt używa:
- Spring Boot `3.5.13`
- Spring AI BOM `1.1.4`
- Embabel `0.4.0-SNAPSHOT`

## Konfiguracja

Klucz OpenAI jest ładowany z lokalnego pliku `.env`.

Utwórz plik `.env` w katalogu głównym projektu:

```dotenv
OPENAI_API_KEY=twoj_klucz_openai
```

Plik `.env` jest ignorowany przez Git i nie powinien trafiać do repozytorium.

## Uruchomienie

W PowerShell:

```powershell
Set-Location "C:\Data\Java\SpringBabelRag"
$env:JAVA_HOME="C:\Users\micha\.jdks\ms-25.0.2"
.\mvnw.cmd spring-boot:run
```
lub zwyczajnie:
```powershell
.\start.cmd
```

Jeśli `java` nie wskazuje na JDK 25, aplikacja może zakończyć się błędem `UnsupportedClassVersionError`.

## Build

Kompilacja bez testów:

```powershell
Set-Location "C:\Data\Java\SpringBabelRag"
.\mvnw.cmd -DskipTests compile
```

Walidacja projektu:

```powershell
Set-Location "C:\Data\Java\SpringBabelRag"
.\mvnw.cmd validate
```

## Jak używać

Po uruchomieniu możesz użyć poleceń Embabel shell i zadać temat wpisu, np.:

```text
Wyjasnij działanie virtualnych wątków w Java
```

Gotowy wpis zostanie zapisany jako plik Markdown w katalogu `blog-posts/`.

### Komenda aliasowa raportu agentow

Po uruchomieniu shella Embabel mozesz wywolac raport jednym poleceniem:

```text
raport-agentow
```

Opcjonalnie podaj sciezke do innego logu:

```text
raport-agentow --logPath C:\\logs\\intelijLog.txt
```

Raport zostanie zapisany w katalogu `reports/`.

## Struktura projektu

- `src/main/java/com/example/spring_babel_rag/` - kod aplikacji
- `src/main/resources/application.yaml` - konfiguracja Spring i Embabel
- `.env` - lokalny sekret OpenAI
- `blog-posts/` - wygenerowane wpisy

## Uwagi

- Jeśli pojawi się błąd `401`, najczęściej oznacza to nieprawidłowy lub niewczytany klucz OpenAI.
- Jeśli wcześniej ujawniono klucz API, należy go zrotować w panelu OpenAI.


