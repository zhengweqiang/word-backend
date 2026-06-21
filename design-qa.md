# Student Workspace Design QA

- Source visual truth: `/Users/wyn/code/word-backend/docs/superpowers/prototypes/student-post-login-mobile/artifacts/home-390x844.jpg` and `study-390x844.jpg`
- Implementation: `http://localhost:8084/`
- Viewport: `390 x 844` (primary), `320 x 740` (responsive check)
- State: authenticated student, real dashboard and study queue data
- Full-view comparison: `docs/superpowers/artifacts/student-home-comparison-390.png`
- Focused comparison: `docs/superpowers/artifacts/student-study-comparison-390.png`
- Syllable reader evidence: `docs/superpowers/artifacts/student-syllable-reader-390.png`

**Findings**
- No actionable P0, P1, or P2 findings remain.
- Typography matches the prototype's Georgia/Songti display hierarchy and PingFang body hierarchy. Dynamic long student names use a smaller narrow-screen size to prevent three-line overflow.
- Layout matches the 430px phone frame, 24px content gutter, 8px card radius, paper background, task ring, grouped reminders, and fixed bottom navigation.
- Colors map to the prototype's brown action, blue information, green new-word, and red overdue tokens.
- Phosphor icons are used consistently with the source. The source contains no raster product imagery requiring asset recreation.
- App-specific static copy follows the source. Differences in names, counts, reminders, plan names, and words are expected real-data substitutions.
- The study card is shorter when a real word lacks phonetic data; no fake placeholder is inserted. Structured syllable data renders inside the card and remains scrollable above the fixed navigation.

**Open Questions**
- None blocking. Wrong-word, favorites, tests, and statistics remain outside the agreed first production slice.

**Implementation Checklist**
- [x] Match the prototype shell and bottom navigation.
- [x] Bind the prototype home composition to the aggregated dashboard API.
- [x] Bind the prototype study card to real recording and retry semantics.
- [x] Add responsive syllable playback with US/UK selection and speech fallback.
- [x] Match library and profile screens to the same visual system.
- [x] Verify 390x844 and 320x740 layouts.

**Patches Made Since Previous QA Pass**
- Replaced the unintended desktop SaaS rail with the prototype phone-frame shell.
- Restored prototype typography, palette, task ring, cards, icon navigation, and button treatments.
- Prevented two-digit task counts and long plan names from wrapping or clipping controls.
- Restored the primary action's 24px/700 type after a global font shorthand override.
- Clamped queue definitions and added content-aware narrow-screen greeting sizing.

**Follow-up Polish**
- P3: add real phonetic metadata during content backfill so more study cards match the reference's phonetic line.

final result: passed
