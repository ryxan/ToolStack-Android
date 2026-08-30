# Bearing dataset future TODO

## Known collision / residual-data issues

- The wide/thicken `62000` series (10–40 mm bore) and the `62200`/`62300` series overlap dimensionally with the double-row `4200`/`4300` series in some sizes (e.g. `4200` vs `62200`). The UI currently can display multiple matches, which may be confusing for users. Consider surfacing a disambiguation hint or grouping by series/type when multiple bearings share the same `d × D × B`.
- Some uncommon wide/thicken variants above 60 mm bore (`62215`–`62220`, `62313`–`62320`) and the full `62000`/`63000` ranges have conflicting or sparse manufacturer data. A future pass should cross-check these against an authoritative ISO 15 source (e.g. SKF/Timken/NSK catalog PDFs) and add the verified entries.
- The `16000` and `16100` narrow series, `61800`/`61900` thin-section series, and `600` series miniature bearings were added from Timken/PTI/JVB catalogs; verify against ISO 15 for any missing intermediate sizes (e.g. `61814`–`61825`, `61914`–`61925`) if they are not already covered.
- Consider adding a changelog or `source` field to `bearings.json` so it is easier to trace the provenance of each entry.
