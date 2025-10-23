### Author: Mattis Schulte
### Date: October 23, 2025

---

### Project Name: SpellChain — A Spring Boot Demo
#### Tested on: Windows 11, Java 17 (Temurin), Maven 3.9.x

> Live demo: https://spellchain.mattisschulte.io/ \
> Original Python implementation:
  https://github.com/Mattis-Schulte/SpellChain/tree/python-based

---

### Setup and Execution Instructions:

Prerequisites:
- Java 17 (JDK)
- Maven 3.9+ (to build and run)

Build:
In the project root (where pom.xml is), run:
```
mvn clean package
```

Run (option A: via Maven):
```
mvn spring-boot:run
```

Run (option B: via JAR):
```
java -jar target/spellchain-server-0.0.1-SNAPSHOT.jar
```

Default server port:
- 8081 (configurable via application.yml or -Dserver.port=XXXX)
- Once running, open http://localhost:8081/ in your browser.