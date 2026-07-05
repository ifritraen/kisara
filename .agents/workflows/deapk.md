---
description: Decode smali bytecode from an extracted APK folder into readable Java source — either the full codebase or specific feature(s) only.
---

# /deapk — APK Smali Decoder Workflow

Triggered when the user executes `/deapk` or asks to decode an extracted APK folder.

---

## PHASE 0 — READ THE USER'S INTENT BEFORE DOING ANYTHING

Before any file operation, answer these two questions from the user's message:

**Q1. What is the extracted APK folder path?**
- If the user provided an explicit path (e.g. `D:\SUSApk\com.example_extracted`), use it.
- If not provided, look for a folder in the current directory that contains `AndroidManifest.xml` and a `smali` or `smali_classes*` subdirectory.
- If still not found, STOP and ask: *"Please provide the path to the extracted APK folder (the one containing AndroidManifest.xml and smali folders)."*

**Q2. Mode — Full Decode or Feature-Specific?**
- If user said "decode everything", "full decode", "whole app", "all code" → set MODE = `FULL`
- If user named specific features (e.g. "login flow", "payment screen", "notification service", "camera") → set MODE = `FEATURE`
  - Record the list of feature keywords as FEATURES = [keyword1, keyword2, …]
- If unclear, STOP and ask: *"Do you want to decode the entire APK, or only specific feature(s)? If specific, please name the feature(s)."*

---

## PHASE 1 — VALIDATE THE EXTRACTED FOLDER STRUCTURE

1. List the root of the provided extracted folder.
2. Confirm these items exist:
   - `AndroidManifest.xml` ← required
   - At least one of: `smali/`, `smali_classes2/`, `smali_classes3/` etc. ← required
   - `res/` ← optional but expected
   - `apktool.yml` ← optional marker confirming this was extracted with apktool
3. If `AndroidManifest.xml` or all `smali*/` folders are missing:
   - STOP and report: *"This does not look like a valid apktool-extracted APK folder. Ensure you ran `/exapk` first."*
4. Identify ALL smali class folders:
   ```powershell
   Get-ChildItem -Path "<extracted_folder>" -Directory | Where-Object { $_.Name -match "^smali" }
   ```
   Save this list as SMALI_DIRS.

---

## PHASE 2 — SET UP OUTPUT DIRECTORY

1. Create a decode output folder named `decoded_<apk_folder_name>` adjacent to the extracted folder:
   ```
   <parent_of_extracted_folder>\decoded_<apk_folder_name>\
   ```
2. Inside it, create a subdirectory: `java_src\`
   ```powershell
   New-Item -ItemType Directory -Force -Path "<output_dir>\java_src"
   ```
3. Record the full path as DECODE_OUT.

---

## PHASE 3 — TOOL CHECK (Run ONCE before any decode)

Check if `jadx` is available:
```powershell
jadx --version
```
- If found → use `jadx` as the decode tool. Set TOOL = `jadx`
- If not found, check for `jadx.bat` or `jadx-gui.bat` in common locations:
  - `C:\tools\jadx\bin\jadx.bat`
  - Any folder in `%PATH%` containing `jadx`
- If jadx is completely absent, check for the original `.apk` file alongside the extracted folder and attempt `jadx` on the `.apk` directly.
- If no jadx at all → STOP and report: *"jadx is required but not found. Install it from https://github.com/skylot/jadx/releases and ensure it is in PATH."*

---

## PHASE 4A — FULL DECODE (if MODE = `FULL`)

Run jadx to decompile ALL smali into Java source:

```powershell
jadx --input-format apk `
     --output-dir "<DECODE_OUT>\java_src" `
     --no-res `
     --show-bad-code `
     --threads-count 4 `
     "<path_to_original.apk>"
```

> If the original .apk is not present, run jadx on the extracted smali folders directly:
```powershell
jadx --input-format smali `
     --output-dir "<DECODE_OUT>\java_src" `
     --show-bad-code `
     --threads-count 4 `
     "<extracted_folder>\smali"
```
If there are multiple `smali_classes*` folders, repeat for each.

Wait for completion. Check that `<DECODE_OUT>\java_src\` contains `.java` files.

---

## PHASE 4B — FEATURE-SPECIFIC DECODE (if MODE = `FEATURE`)

### Step 4B.1 — Read AndroidManifest.xml

Open and read `<extracted_folder>\AndroidManifest.xml` in full.

Extract and list:
- The `package` attribute of the root `<manifest>` element → this is BASE_PACKAGE (e.g. `com.example.app`)
- All `<activity>` names
- All `<service>` names
- All `<receiver>` names
- All `<provider>` names

### Step 4B.2 — Map Features to Smali Packages

For each keyword in FEATURES:
1. Search for matching class/package names in ALL smali folders:
   ```powershell
   Get-ChildItem -Path "<extracted_folder>" -Recurse -Filter "*.smali" |
     Where-Object { $_.FullName -match "<keyword>" } |
     Select-Object FullName
   ```
   Replace `<keyword>` with the feature keyword (case-insensitive).
2. Also search inside smali file contents for the keyword:
   ```powershell
   Select-String -Path "<extracted_folder>\**\*.smali" -Pattern "<keyword>" -CaseSensitive:$false |
     Select-Object -ExpandProperty Path | Sort-Object -Unique
   ```
3. Collect all unique `.smali` file paths found across both searches into MATCHED_FILES[keyword].
4. Derive the unique parent package directories from those file paths → MATCHED_PACKAGES[keyword].
5. If MATCHED_FILES[keyword] is empty → STOP for this feature and report: *"No smali classes found matching '<keyword>'. Verify the feature name or try a different search term."*

### Step 4B.3 — Decode Only Matched Smali Files

For each matched `.smali` file collected across all features:

1. Ensure the output subdirectory mirrors the smali path structure under `<DECODE_OUT>\java_src\`:
   ```powershell
   $relPath = $file.FullName.Replace("<extracted_folder>\smali\", "")
   $outDir  = Split-Path "<DECODE_OUT>\java_src\$relPath"
   New-Item -ItemType Directory -Force -Path $outDir
   ```

2. Decode each `.smali` file to `.java` using `jadx` on the individual file, OR use `enjarify` + `cfr`/`procyon` as fallback.

   **Preferred: jadx on whole APK, then filter:**
   ```powershell
   jadx --input-format apk `
        --output-dir "<DECODE_OUT>\java_src" `
        --no-res `
        --show-bad-code `
        --threads-count 4 `
        "<path_to_original.apk>"
   ```
   Then DELETE all generated `.java` files whose class path does NOT match any path in MATCHED_FILES. Use:
   ```powershell
   $matchedClasses = @("<class/path/1>", "<class/path/2>") # built from MATCHED_FILES
   Get-ChildItem -Path "<DECODE_OUT>\java_src" -Recurse -Filter "*.java" |
     Where-Object {
       $rel = $_.FullName.Replace("<DECODE_OUT>\java_src\", "").Replace("\","/")
       -not ($matchedClasses | Where-Object { $rel -like "*$_*" })
     } | Remove-Item -Force
   ```

3. Confirm which `.java` files remain in `<DECODE_OUT>\java_src\`.

---

## PHASE 5 — POST-DECODE INSPECTION

After decoding (FULL or FEATURE):

1. List all generated `.java` files:
   ```powershell
   Get-ChildItem -Path "<DECODE_OUT>\java_src" -Recurse -Filter "*.java" | Select-Object FullName, Length
   ```

2. Count total files decoded:
   ```powershell
   (Get-ChildItem -Path "<DECODE_OUT>\java_src" -Recurse -Filter "*.java").Count
   ```

3. For FEATURE mode: Print a mapping table showing which feature keyword matched which decoded Java class files.

4. Open and display the first 50 lines of the most relevant decoded file (the one whose name most closely matches the primary feature keyword, or `MainActivity.java` for FULL mode).

---

## PHASE 6 — SUMMARY REPORT

Print the following summary exactly:

```
=== /deapk COMPLETE ===
Mode         : <FULL | FEATURE>
Source Folder: <extracted_folder>
Output Folder: <DECODE_OUT>
Java Files   : <count>

Feature Mapping (FEATURE mode only):
  <keyword1> → <N> files decoded
  <keyword2> → <N> files decoded

Key decoded files:
  - <DECODE_OUT>\java_src\<package>\<ClassName>.java
  - ...
```

Record `<DECODE_OUT>` in `.aaa/findings.md` under a `## Decoded APKs` section.
