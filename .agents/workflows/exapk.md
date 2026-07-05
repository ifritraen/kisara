---
description: Decompile and extract the contents of a specified .apk file using apktool.
---
When the user executes `/exapk` or requests to extract/decompile an APK:
1. **Identify the Target APK**: 
   - Locate the APK file path provided by the user.
   - If not specified, look for any `.apk` file in the current working directory or subdirectories, or ask the user to specify the path to the `.apk` file.
2. **Define Output Location**:
   - Create an output directory based on the APK's name (e.g., `extracted_<apk_name>`).
   - By default, place this folder in the same directory as the target APK.
3. **Execute Extraction**:
   - Run the decompile command using `apktool`:
     ```powershell
     apktool d "path\to\target.apk" -o "path\to\output_directory"
     ```
   - If `apktool` command is not found in the environment PATH, fall back to running it via java (if `apktool.jar` is present):
     ```powershell
     java -jar apktool.jar d "path\to\target.apk" -o "path\to\output_directory"
     ```
4. **Summarize Extracted Assets**:
   - Inform the user of the successful extraction.
   - List the key generated directories and files, such as [AndroidManifest.xml](file:///path/to/output_directory/AndroidManifest.xml) and folders like `res` and `smali`.
