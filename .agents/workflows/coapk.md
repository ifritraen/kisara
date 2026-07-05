---
description: Reconstruct working feature code(s) from an extracted or decoded APK — decodes first if needed, then produces clean, readable, ready-to-use source files for the requested feature(s).
---

# /coapk — APK Feature Code Reconstructor Workflow

Triggered when the user executes `/coapk` or asks to copy/reconstruct feature code from an APK.

---

## PHASE 0 — READ USER INTENT BEFORE TOUCHING ANY FILES

Before anything, collect three things from the user's message:

**Q1. What is the source folder?**
- Could be an **extracted** APK folder (contains `smali/`, `AndroidManifest.xml`)
- Could be a **decoded** folder (contains `java_src/` with `.java` files)
- If the user gave a path, use it. If not, look in current directory for either structure.
- If neither is found → STOP and ask: *"Please provide the path to the extracted APK folder or an already-decoded java_src folder."*

**Q2. What feature(s) do you want to reconstruct?**
- User must name at least one feature (e.g. "login", "camera upload", "push notification handler", "in-app purchase flow").
- If not specified → STOP and ask: *"Which feature(s) do you want to extract and reconstruct? Please name them."*
- Record the list as TARGET_FEATURES = [feature1, feature2, …]

**Q3. Where should the output go?**
- Default: create a new folder called `coapk_<feature>_output` adjacent to the source folder.
- If user specified an output path, use it.
- Record as COAPK_OUT.

---

## PHASE 1 — DETECT INPUT STATE: EXTRACTED or DECODED

**Inspect the source folder:**

Check 1 — Is it already decoded?
```powershell
Test-Path "<source_folder>\java_src"
```
- If `java_src\` exists and contains `.java` files → set STATE = `DECODED`
- Skip directly to PHASE 3.

Check 2 — Is it an extracted APK folder?
```powershell
Test-Path "<source_folder>\AndroidManifest.xml"
Get-ChildItem -Path "<source_folder>" -Directory | Where-Object { $_.Name -match "^smali" }
```
- If `AndroidManifest.xml` exists AND at least one `smali*` folder exists → set STATE = `EXTRACTED`
- Proceed to PHASE 2 (decode first).

If neither check passes → STOP and report: *"Cannot determine folder type. The folder must contain either 'java_src/' (decoded) or 'AndroidManifest.xml + smali/' (extracted). Please verify the path."*

---

## PHASE 2 — DECODE FIRST (only if STATE = `EXTRACTED`)

This phase mirrors /deapk's PHASE 3 and PHASE 4B exactly. Follow every sub-step below.

### 2.1 — Tool Check
```powershell
jadx --version
```
- Found → TOOL = `jadx`
- Not found → search for `jadx.bat` in common paths.
- Not found anywhere → STOP: *"jadx is required. Install from https://github.com/skylot/jadx/releases"*

### 2.2 — Read AndroidManifest.xml
Open `<source_folder>\AndroidManifest.xml`.
Extract:
- `package` attribute → BASE_PACKAGE (e.g. `com.example.app`)
- All `<activity>` class names
- All `<service>` class names
- All `<receiver>` class names
- All `<provider>` class names

### 2.3 — Map Each Target Feature to Smali Files

For EACH keyword in TARGET_FEATURES:

**Step A — Search file names:**
```powershell
Get-ChildItem -Path "<source_folder>" -Recurse -Filter "*.smali" |
  Where-Object { $_.Name -match "<keyword>" } |
  Select-Object FullName
```

**Step B — Search file contents:**
```powershell
Select-String -Path "<source_folder>\**\*.smali" -Pattern "<keyword>" -CaseSensitive:$false |
  Select-Object -ExpandProperty Path | Sort-Object -Unique
```

**Step C — Also search manifest entries:**
Scan the activity/service/receiver names extracted in 2.2 for the keyword string.
Map matched manifest class names back to their `.smali` file paths.

**Step D — Collect:**
Combine results from A+B+C. Remove duplicates. Store as SMALI_MAP[keyword].

If SMALI_MAP[keyword] is empty → report: *"No smali code found for '<keyword>'. Try an alternative keyword."* Continue with other features.

### 2.4 — Run jadx to Decode the Entire APK (for accuracy)
```powershell
jadx --input-format apk `
     --output-dir "<COAPK_OUT>\java_src" `
     --no-res `
     --show-bad-code `
     --threads-count 4 `
     "<path_to_original.apk_if_available>"
```
If the `.apk` file is not available, run jadx on the smali folder:
```powershell
jadx --input-format smali `
     --output-dir "<COAPK_OUT>\java_src" `
     --show-bad-code `
     "<source_folder>\smali"
```
If multiple `smali_classes*` folders exist, run jadx once per folder. Wait for full completion.

Set STATE = `DECODED` and DECODED_DIR = `<COAPK_OUT>\java_src`.

---

## PHASE 3 — LOCATE FEATURE FILES IN THE DECODED JAVA SOURCE

If STATE was already `DECODED` from Phase 1, set DECODED_DIR = `<source_folder>\java_src`.

For EACH keyword in TARGET_FEATURES:

**Step A — Search decoded Java files by name:**
```powershell
Get-ChildItem -Path "<DECODED_DIR>" -Recurse -Filter "*.java" |
  Where-Object { $_.Name -match "<keyword>" } |
  Select-Object FullName
```

**Step B — Search decoded Java file contents:**
```powershell
Select-String -Path "<DECODED_DIR>\**\*.java" -Pattern "<keyword>" -CaseSensitive:$false |
  Select-Object -ExpandProperty Path | Sort-Object -Unique
```

**Step C — Cross-reference with manifest entries:**
If you previously parsed AndroidManifest.xml (or parse it now from `<source_folder>\AndroidManifest.xml`),
find activity/service/receiver names matching the keyword.
Look up the exact Java file for each matched class in DECODED_DIR.

**Step D — Determine Primary Class:**
Among all matched files, identify the PRIMARY class — the one whose name most directly matches the keyword (e.g. `LoginActivity.java` for "login"). If multiple candidates tie, list all and select the first alphabetically.

**Step E — Collect dependencies of the Primary Class:**
Open the PRIMARY class Java file.
Read its `import` statements. For each imported class that starts with BASE_PACKAGE:
- Locate the corresponding `.java` file in DECODED_DIR.
- Add it to DEPS[keyword].
- Recursively read that file's imports and add transitive BASE_PACKAGE imports (limit: 2 levels deep to avoid collecting the whole app).

Compile FEATURE_FILES[keyword] = PRIMARY file + all DEPS[keyword] files.

If FEATURE_FILES[keyword] is empty after all steps → report: *"Cannot locate Java source for '<keyword>'. The smali may have failed to decompile. Check <COAPK_OUT>\java_src for errors."*

---

## PHASE 4 — RECONSTRUCT AND ASSEMBLE FEATURE OUTPUT

For EACH keyword in TARGET_FEATURES (use its FEATURE_FILES[keyword]):

### 4.1 — Create Feature Output Folder
```powershell
New-Item -ItemType Directory -Force -Path "<COAPK_OUT>\features\<keyword>"
```

### 4.2 — Copy All Feature Files Into It (preserve package subfolder structure)
For each file in FEATURE_FILES[keyword]:
```powershell
$relPath = $file.FullName.Replace("<DECODED_DIR>\", "")
$dest    = "<COAPK_OUT>\features\<keyword>\$relPath"
$destDir = Split-Path $dest
New-Item -ItemType Directory -Force -Path $destDir
Copy-Item -Path $file.FullName -Destination $dest
```

### 4.3 — Copy Related Resources (layout XML, drawables, strings)
Inspect the PRIMARY Java file for references to R.layout.*, R.drawable.*, R.string.*, R.id.*, R.menu.*:
```powershell
Select-String -Path "<PRIMARY_JAVA_FILE>" -Pattern "R\.(layout|drawable|string|menu|id)\.\w+" |
  Select-Object -ExpandProperty Matches | ForEach-Object { $_.Value }
```

For each resource reference found:
- Find the corresponding file in `<source_folder>\res\`:
  - `R.layout.my_layout` → `res\layout\my_layout.xml`
  - `R.drawable.icon` → `res\drawable\icon.png` (or any drawable folder variant)
  - `R.string.title` → copy the matching entry from `res\values\strings.xml` (copy the whole file if needed)
- Copy found resource files into `<COAPK_OUT>\features\<keyword>\res\<subfolder>\`

### 4.4 — Copy Manifest Entries
From `<source_folder>\AndroidManifest.xml`, extract the XML blocks (`<activity>`, `<service>`, `<receiver>`) that correspond to classes in FEATURE_FILES[keyword].
Write them into `<COAPK_OUT>\features\<keyword>\manifest_entries.xml`:
```xml
<!-- Manifest entries for feature: <keyword> -->
<!-- Add these inside your <application> tag -->
<activity android:name=".XxxActivity" ... />
<service android:name=".XxxService" ... />
```

### 4.5 — Write a Feature README
Create `<COAPK_OUT>\features\<keyword>\README.md` with:
```markdown
# Feature: <keyword>

## Source APK Base Package
<BASE_PACKAGE>

## Primary Class
<PRIMARY_CLASS_FULL_NAME>

## All Included Java Files
- <relative path 1>
- <relative path 2>
- ...

## Included Resources
- <res/layout/...>
- <res/drawable/...>
- <res/values/strings.xml> (relevant strings only, or full file)

## Manifest Entries Required
See manifest_entries.xml — add these to your AndroidManifest.xml.

## Integration Notes
- Change the package name in all files from `<BASE_PACKAGE>` to your own package.
- Update import statements accordingly.
- Add any required permissions listed in the original manifest to your own manifest.
```

---

## PHASE 5 — PERMISSIONS AUDIT

Open `<source_folder>\AndroidManifest.xml`.
Find all `<uses-permission>` tags.
Cross-reference: which permissions are accessed by the classes in FEATURE_FILES (check smali strings or Java file strings for permission names).
Write a `permissions_required.txt` to `<COAPK_OUT>\features\<keyword>\`:
```
Permissions required by feature '<keyword>':
  android.permission.INTERNET
  android.permission.CAMERA
  ... (list all matched ones)

Add these to your AndroidManifest.xml:
  <uses-permission android:name="android.permission.INTERNET" />
  ...
```

---

## PHASE 6 — FINAL SUMMARY REPORT

Print this exactly:

```
=== /coapk COMPLETE ===
Input State   : <EXTRACTED | DECODED>
Source Folder : <source_folder>
Output Folder : <COAPK_OUT>

Features Reconstructed:
  <keyword1>
    Primary Class : <FullClassName>
    Java Files    : <N>
    Resources     : <N>
    Output        : <COAPK_OUT>\features\<keyword1>\
  <keyword2>
    ...

Next Steps:
  1. Open <COAPK_OUT>\features\<keyword1>\README.md for integration instructions.
  2. Copy the java files into your project's package structure.
  3. Merge manifest_entries.xml into your AndroidManifest.xml.
  4. Add permissions from permissions_required.txt.
  5. Update all import statements with your own package name.
```

Record `<COAPK_OUT>` in `.aaa/findings.md` under a `## Reconstructed Features` section.
