# app/libs — Glyph Matrix SDK (not included)

GlyphWave needs Nothing's **Glyph Matrix SDK** AAR. Nothing's licence does not allow redistributing it,
so it is gitignored (`app/libs/*.aar`) and you must download it yourself.

1. Download `glyph-matrix-sdk-2.0.aar` from the official kit:
   https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
   (file at the repo root — or clone: `git clone --depth 1 https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit`)
2. Copy it here **renamed** to `GlyphMatrixSDK.aar`:

   ```bash
   cp GlyphMatrix-Developer-Kit/glyph-matrix-sdk-2.0.aar app/libs/GlyphMatrixSDK.aar
   ```

   PowerShell:

   ```powershell
   Copy-Item GlyphMatrix-Developer-Kit\glyph-matrix-sdk-2.0.aar app\libs\GlyphMatrixSDK.aar
   ```

The build fails with a clear message if the file is missing.
By using the SDK you accept Nothing's Glyph SDK EULA (non-commercial use unless Nothing grants permission).
