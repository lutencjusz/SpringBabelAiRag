<div align="right">PL: <a href="README_PL.md">README_PL.md</a></div>

# SpringBabelAiRag

A simple `Spring Boot` + `Embabel` project for generating blog posts using OpenAI models.

The application:
- accepts a question or topic,
- translates the input into English,
- creates a draft blog post,
- improves the technical quality of the content,
- translates the final result into Polish,
- saves the finished post to the `blog-posts/` directory.

## Requirements

- Java 25
- Git
- access to the OpenAI API

The project uses:
- Spring Boot `3.5.13`
- Spring AI BOM `1.1.4`
- Embabel `0.4.0-SNAPSHOT`

## Configuration

The OpenAI key is loaded from a local `.env` file.

Create a `.env` file in the project root directory:

```dotenv
OPENAI_API_KEY=twoj_klucz_openai
```

The `.env` file is ignored by Git and should not be committed to the repository.

## Running

In PowerShell:

```powershell
Set-Location "C:\Data\Java\SpringBabelRag"
$env:JAVA_HOME="C:\Users\micha\.jdks\ms-25.0.2"
.\mvnw.cmd spring-boot:run
```

If `java` does not point to JDK 25, the application may fail with `UnsupportedClassVersionError`.

## Build

Compile without tests:

```powershell
Set-Location "C:\Data\Java\SpringBabelRag"
.\mvnw.cmd -DskipTests compile
```

Project validation:

```powershell
Set-Location "C:\Data\Java\SpringBabelRag"
.\mvnw.cmd validate
```

## How to use

After starting the application, you can use Embabel shell commands and provide a blog topic, for example:

```text
Wyjasnij działanie virtualnych wątków w Java
```

The generated post will be saved as a Markdown file in the `blog-posts/` directory.

## Project structure

- `src/main/java/com/example/spring_babel_rag/` - application code
- `src/main/resources/application.yaml` - Spring and Embabel configuration
- `.env` - local OpenAI secret
- `blog-posts/` - generated blog posts

## Notes

- If a `401` error appears, it usually means the OpenAI key is invalid or was not loaded.
- If the API key was previously exposed, it should be rotated in the OpenAI dashboard.

