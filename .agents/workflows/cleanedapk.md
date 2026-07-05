---
description: Build a cleaned, rebuilt APK from scratch or decompiled sources using custom security filters, zipalignment, and private signature signing.
---
When the user executes `/cleanedapk` or requests cleaning/rebuilding an APK:
1. **Identify Project/Report**: Read target's Cleaning Blueprint from `first_checkup_report.md` and set up `CleanedApk/Processing_Components/{AppName}_process`.
2. **Execute Cleaning Operations**: Strip dangerous permissions, services/receivers (Accessibility/Notification listener), purge asset binaries, and sanitize C2 smali code.
3. **Compile & Rebuild**: Compile decompiled sources using `apktool b` or compile clean Flutter release splits.
4. **Zipalign Optimization**: Optimize the compiled binary using `zipalign -v -f 4`.
5. **Secure Signature Signing**: Generate a custom signing keystore and run `apksigner` to resolve default debug certificate detections.
6. **Outputs & Registration**: Save final output in `CleanedApk/Outputs/`, write findings, and register to megadir.md.
