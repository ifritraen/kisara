---
description: Run sequentially through all checkups on a suspicious APK using upgraded Checker rules, calculate risk scores, and generate cleanup guides.
---
When the user executes `/checkapk` or provides an APK file under `SUSApk`:
1. **Identify the Target APK**: Locate APK under SUSApk and extract the folder/application name.
2. **Decompile/Extract the APK**: Run apktool.jar to decompile the target APK.
3. **Execute Sequential Checks from Checker**: Run manifest inspection, resource auditing, security analysis, and VirusTotal API checks.
4. **Calculate Security Risk Score**: Synthesize and average the four section threat scores (0-100).
5. **Write Reports with Cleaning Instructions**: Save first_checkup_report.md containing scores, findings, and a clear Cleaning Blueprint.
6. **Register to megadir.md**: Add folder paths to the global directory registry.
