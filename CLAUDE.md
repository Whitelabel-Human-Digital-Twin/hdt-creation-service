# hdt-creation-service

Kotlin/Ktor microservice that parses CRF (Case Report Form) Excel files into Human Digital Twin (HDT) domain objects and forwards them to the `persistence-service` for storage.

## System context

This service sits between a frontend upload form and the `persistence-service` (runs on `localhost:8081`, endpoint `PUT /hdts/batch`). It is part of the broader WHDT monitoring platform in the `whdt-monitor-workspace`.

## Tech stack

- **Kotlin 2.3.10**, **Ktor 3.2.1** (Netty engine)
- **Apache POI 5.4.0** for `.xlsx` parsing
- **kotlinx-serialization** for JSON
- `io.github.whdt:whdt-core` and `io.github.whdt:whdt-distributed` (private GitHub Packages)

## Build & run

```bash
./gradlew run          # starts on port 8080
./gradlew test
./gradlew build
./gradlew buildFatJar  # produces a fat JAR
```

## GitHub Packages credentials

The `whdt-core` and `whdt-distributed` libraries are hosted on GitHub Packages. Set these before building:

```bash
# Option A — environment variables
export GPR_USER=<github-username>
export GPR_TOKEN=<github-personal-access-token>

# Option B — ~/.gradle/gradle.properties
gpr.user=<github-username>
gpr.key=<github-personal-access-token>
```

## API

### `POST api/hdts/multipart`

Multipart upload. Required field: `file` (`.xlsx`, max 30 MB).

1. Parses the workbook into HDTs.
2. Writes a JSON dump and a human-readable report to `logs/`.
3. Forwards the HDT list to `http://localhost:8081/hdts/batch`.
4. Returns `200 OK` on success.

### `POST api/hdts/sensor/multipart`

Multipart upload of one sensor CSV = **one subject × one sensor**. Required field: `file` (`.csv`, max 64 MB). Optional text fields `patientId` / `task` / `sensor` override the values parsed from the filename.

- **Identifiers** come from the filename `<patientId>_<task>_<sensor>.csv` (e.g. `01A101_nw_acc`); a non-blank form field wins over the parsed token. If any identifier is still missing → `400`.
- **Header row** = property names, used verbatim (e.g. `sens1_x`); `declaredType = DOUBLE`, no normalization/axis parsing.
- Builds a `Model` named `<sensor>` on HDT `<patientId>` (a shell HDT is created if absent), one `Property` per column.
- Each data row is a frame; for frame `f` it emits one `PropertyObservation` per column with `value` = the cell, `timestamp` = a monotonic `ingestBase + f` (strictly increasing), and `metadata = { "task": task, "frame": f }`.
- Upserts HDT+model via `PUT /hdts/batch`, then streams observations to `POST /observations/batch` in chunks of `OBSERVATION_CHUNK_SIZE` (5 000). Returns `201 Created`.

Pipeline lives in `io.github.whdt.crf.csv` (`SensorCsvNaming`, `CsvSensorReader`, `CsvSensorAssembler`); parsing uses Apache Commons CSV.

## Domain: CRF format

CRF = **Case Report Form** — a clinical research spreadsheet with this structure:

| Concept | Excel representation |
|---|---|
| Visit type / model | Sheet name (normalized → `model_name`) |
| Patient | Row; identified by the patient ID column |
| Visit timestamp | Date column (optional) |
| Clinical property | Any other non-blank column with a non-blank value |

## Import pipeline

```
.xlsx file
  └─ CrfWorkbookExtractor   → RawWorkbook (sheets → rows → cells)
       └─ CrfSheetInterpreter (per sheet)
            • skips excluded sheets (sigle, legend, legenda by default)
            • detects header row by scanning for a patient ID column
            • resolves patient_id and date columns via ColumnResolver
            • normalizes all names via CrfNameNormalizer
            → List<ParsedVisitRow>
                 └─ CrfDomainAssembler
                      • groups rows by patientId
                      • one Model per ParsedVisitRow (sheet = model name)
                      • derives a "meta" model with delta_age from baseline data
                      → List<HumanDigitalTwin>
```

## Name normalization (`CrfNameNormalizer`)

All sheet names, column headers, and property names are normalized: lowercase, Italian accent folding (àéì… → aei…), non-alphanumeric → `_`, consecutive underscores collapsed.

## Column resolution (`ColumnResolver`)

Columns are matched by normalized exact alias. Aliases are configured in `CrfImportConfig`. Pattern-based fallback exists in the code but is currently commented out.

**Deduplication**: if a patient ID appears more than once in a sheet, the last row wins (with a WARNING log entry).

## Key configuration (`CrfImportConfig`)

| Field | Default |
|---|---|
| `excludedSheetNames` | `{sigle, legend, legenda}` |
| `patientIdAliases` | `id paziente`, `id_paziente`, `patient_id`, … |
| `visitDateAliases` | `data visita`, `visit date`, `data`, … |

The config is instantiated inline in `Routing.kt`; change it there to add new aliases or exclude additional sheets.

## Logging

After each import, two files are written to `logs/` (ignored by git):
- `output_<timestamp>.json` — full HDT list (pretty-printed)
- `report_<timestamp>.txt` — human-readable import report

Logging failures do not abort the request.

## Package structure

```
io.github.whdt.crf
├── Application.kt          // Ktor app setup, CORS
├── Routing.kt              // HTTP endpoint wiring
├── CrfDomainAssembler.kt   // ParsedVisitRow → HumanDigitalTwin
├── importer/
│   ├── CrfImportService.kt     // pipeline orchestrator
│   ├── CrfWorkbookExtractor.kt // POI → RawWorkbook
│   ├── CrfImportConfig.kt      // column aliases & exclusions
│   ├── CrfColumnResolver.kt    // resolves a column by alias
│   ├── CrfNameNormalizer.kt    // string normalization
│   ├── model/                  // Raw* and Parsed* data classes, ImportReport
│   └── util/                   // multipart helpers, date utils, logging utils
├── interpreter/
│   └── CrfSheetInterpreter.kt  // sheet → ParsedVisitRow list
└── parser/
    └── CrfValueParser.kt       // raw string → PropertyValue (bool/int/long/double/string)
```