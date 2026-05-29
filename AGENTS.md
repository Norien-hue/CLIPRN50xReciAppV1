# AGENTS.md — Interfaces_PI

## Repository layout
- `referencias/source/` — Complete reference JavaFX app "ReciInventario" (Gradle project, JavaFX 25, JDK 21)
- `referencias/reciclaje_terminal.py` — Python CLI that interacts with the same REST API as the reference app
- `trabajo/` — Student work directory (build your JavaFX app here)

## Reference app (referencias/source/) — key facts
- **Stack**: JavaFX 25, Gradle 8.8, JDK 21 (Eclipse Adoptium), no module-info
- **Entrypoint**: `com.javafx.reciWins.start.StartWin` extends `Application`
- **Main class**: `com.javafx.reciWins.start.StartWin`
- **Views**: FXML in `app/src/main/resources/view/`
- **CSS**: `app/src/main/resources/css/styles.css` — green palette (`#e8f5e9`/`#c8e6c9`/`#81c784`/`#388e3c`/`#2e7d32`)
- **Build**: `gradlew run` from `referencias/source/` (requires JDK 21 in `gradle.properties`)
- **API**: REST at `http://52.201.91.206:3000` (via `ApiClient` singleton, `java.net.http.HttpClient`, JWT auth)
- **No direct DB** — all data through REST API
- **All controllers** in `com.javafx.reciWins.controllers`, models in `com.javafx.model`, utils in `com.javafx.reciWins.utiles`
- **Reports**: JasperReports (PDF), ChartFX (charts), ControlsFX (UI extras), BCrypt (password hashing)
- **Resource whitelist**: `build.gradle` `sourceSets.main.resources.includes` — add new extensions there

## Key development commands (from reference)
```
gradlew run                          # Run the JavaFX app
gradlew jar                          # Build JAR
gradlew clean                        # Clean build
```

## API endpoints (from reciclaje_terminal.py & ApiClient.java)
| Action | Endpoint |
|--------|----------|
| Health check | `GET /api/health` |
| Login | `POST /api/usuarios/login` → returns JWT |
| Find product by barcode | `GET /api/productos/barcode/{code}` |
| Find user by TAP | `GET /api/usuarios/by-tap/{tap}` |
| Register recycling | `POST /api/historial` (auth required) |

## Course context
This is a university "Interfaces" course project. Students should:
1. Study the reference app's architecture, FXML/controller pattern, and CSS styling
2. Build their own JavaFX app in `trabajo/` — camera + product selection + TAP validation + API integration
3. Follow the same visual style (green palette from reference CSS) and MVC pattern

## Gotchas
- Reference app requires JDK 21 (Eclipse Adoptium path hardcoded in `gradle.properties` and `run.bat`)
- API server must be running for the app to function — startup health check at `/api/health`
- No `.gitignore` — build artifacts (`build/`, `.gradle/`, `bin/`) are tracked
- JVM args needed: `--add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED`
- UTF-8 encoding forced in build.gradle for compile/test/javadoc
