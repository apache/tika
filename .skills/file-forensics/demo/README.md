# Demo files — try the skill on these

Real files with real findings to discover (Apache Tika test corpus,
ALv2-licensed, renamed for realism). Ask your agent, for example:

| File | Try asking |
|---|---|
| `contract.pdf` | "Has this PDF been modified since it was first written? Show me what it said before." |
| `quarterly-report.docm` | "Does this document contain macros? Show me the code." |
| `memo.docx` | "What did the author delete or comment on? Show me content this file still carries but doesn't display." |
| `budget.xlsx` | "Where was this spreadsheet last saved, and what does that path reveal about its author's machine?" |

Each is small and safe, and each carries a genuine non-obvious finding —
revision history you can extract as openable prior PDFs, real macro source,
metadata that leaks a local username and path. (Note: tika-app's single-file
mode pre-enables several forensics switches, so some findings appear even
without the skill's config; the skill explains which, and the capture-once /
query workflow is where the investigation value lives.)

Original fixture names (provenance): testPDF_incrementalUpdates.pdf,
testWORD_macros.docm, testWORD_embedded_pics.docx, testEXCEL_big_numbers.xlsx.
