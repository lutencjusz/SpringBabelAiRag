$env:JAVA_HOME="C:\Users\micha\.jdks\ms-25.0.2"
ollama run gemma4:e4b &
ollama serve &
.\mvnw.cmd spring-boot:run

