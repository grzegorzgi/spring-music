# Quality Tasks — Scenariusz 1: Code Modernization

_Ostatnia aktualizacja: 2026-04-24_

---

## ✅ The Pin — Testy charakteryzujące
> Pinuj zachowanie monolitu zanim ktokolwiek go dotknie — błędy włącznie.

- [x] Napisać testy charakteryzujące dla `AlbumController` (22 testy)
- [x] Napisać testy charakteryzujące dla `InfoController` i `ErrorController` (7 testów)
- [x] Udokumentować quirki monolitu w komentarzach testów
- [x] Wszystkie testy zielone (`./gradlew test`)
- [x] PR zmergowany → https://github.com/grzegorzgi/spring-music/pull/3

**Quirki udokumentowane:**
- `GET /albums/{nieistniejące-id}` → 200 + puste body (zamiast 404)
- `DELETE /albums/{nieistniejące-id}` → 500 `EmptyResultDataAccessException` (zamiast 200/404)
- `GET /errors/throw` → `NullPointerException` bez obsługi błędów

**Uwaga:** drugi zespół (Challenge 4) dodał równolegle własne testy charakteryzujące
w pakiecie `org.cloudfoundry.samples.music.characterization` — nasze są w `...music.web`.
Brak konfliktów, obydwa zestawy się uzupełniają.

---

## ⏳ The Fence — Testy granicy między monolitem a nowym serwisem
> Wykryj automatycznie, jeśli wewnętrzne pole monolitu przecieknie do API nowego serwisu.

**Czeka na:** The Cut (ekstrakcja Album Catalog Service) — jeszcze nie zrobiona.

Zgodnie z ADR-001, jako pierwszy zostanie wyekstrahowany **Album Catalog Service** (Seam 2):
- `AlbumController` + wszystkie repozytoria + `Album` + `RandomIdGenerator`
- Po ekstrakcji API nowego serwisu NIE powinno eksponować pola `albumId` (wewnętrzny identyfikator)

Do zrobienia gdy developer skończy The Cut:
- [ ] Napisać test sprawdzający że `albumId` NIE pojawia się w API nowego serwisu
- [ ] Napisać test sprawdzający że żadne inne wewnętrzne pole monolitu nie przecieka
- [ ] Test czerwony przy naruszeniu granicy, zielony gdy granica jest czysta

---

## 🔄 The Scorecard — Eval harness dla refaktoringu przez Claude
> Mierzy jak dobrze Claude radzi sobie z ekstrakcją serwisów. Uruchamia się w CI.

**Pierwsze uruchomienie: 2026-04-24 — wyniki poniżej.**

- [x] Zdefiniować "złoty zestaw" — 12 labeled examples (4 easy / 4 medium / 4 hard)
  - Źródło: ADR-001 + `scouts/seams.json`
  - Plik: `scorecard/golden_dataset.json`
- [x] Napisać harness z validation-retry loop (do 3 prób na przykład)
  - Plik: `scorecard/scorecard.py` (AWS Bedrock, claude-haiku-4-5)
- [x] Metryki: accuracy, precision per class, false-confidence rate, stratified by difficulty
- [x] Pierwsze uruchomienie zakończone sukcesem
- [ ] Naprawić E10 (parse error przy przykładach bez `included` — pustej liście plików)
- [ ] Dodać więcej CORRECT examples do złotego zestawu (obecnie 3/12 to CORRECT)
- [ ] Zintegrować z CI (GitHub Actions)

**Wyniki pierwszego uruchomienia:**

| Metryka | Wynik | Interpretacja |
|---------|-------|---------------|
| Accuracy | 90.9% (11/12) | Dobry wynik na start |
| Precision CORRECT | 100% | Gdy Claude mówi CORRECT — zawsze ma rację |
| Precision INCORRECT | 90% | Gdy Claude mówi INCORRECT — 9 na 10 razy ma rację |
| False-confidence rate | 9.1% (1/11) | 1 błąd przy HIGH confidence |
| easy | 75% (3/4) | Trudniejszy poziom niż się wydaje |
| medium | 100% (4/4) | |
| hard | 100% (3/3) | |

**Zidentyfikowane słabości Claude:**
- **E01 FALSE CONFIDENT**: Pełna ekstrakcja Album Catalog (JPA+Mongo+Redis razem) — Claude oznaczył jako INCORRECT przy HIGH confidence. Prawdopodobna przyczyna: widzi wiele implementacji i uznaje to za za dużą zmianę.
- **E10 parse error**: Przykład "usuń ErrorController" (pusta lista plików) — Claude zwrócił pusty response zamiast JSON. Prompt nie obsługuje dobrze przypadku deletion (nie-ekstrakcji).

---

## Stan pracy zespołu (sync 2026-04-24)

| Challenge | Kto | Status |
|-----------|-----|--------|
| 1 — The Stories | PM | ✅ `stories/USER_STORIES.md` |
| 2 — The Patient | Architect | ✅ `docs/patient-diagnosis.md` |
| 3 — The Map | Architect | ✅ `decisions/ADR-001-decomposition.md` |
| 4 — The Pin | Quality (Igor + inny dev) | ✅ dwa zestawy testów |
| 5 — The Cut | Dev | ⏳ nie zaczęte |
| 6 — The Fence | Quality (Igor) | ⏳ czeka na The Cut |
| 7 — The Scorecard | Quality (Igor) | 🔲 można zacząć |
| 9 — The Scouts | Mariola (agentic) | ✅ `scouts/` |

---

## Legenda
| Symbol | Znaczenie |
|--------|-----------|
| ✅ | Ukończone |
| ⏳ | Czeka na inny zespół |
| 🔲 | Do zrobienia — można zacząć |
