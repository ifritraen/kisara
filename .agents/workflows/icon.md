---
description: Generate a prompt or asset workflow for creating a minimalist, sophisticated, glassy app icon in PNG format, configured as an adaptive icon for Android mobile and TV.
---
When the user executes `/icon` or starts their request with `/icon`:
1. Output the following high-level icon creation prompt for the user:

```text
Create a minimalist and highly sophisticated app icon in high-resolution PNG format.
The design must be centered around a clean, creative visual metaphor representing the core purpose of the app.

Style & Aesthetics:
- Material design-inspired translucent layers ("glassmorphism" / glassy look) with frosted edges.
- Classy, harmonious color palette using a dark slate background, deep obsidian plates, and vibrant cyan-to-purple glowing gradient highlights.
- The layout must fit inside an adaptive Android launcher configuration (centering the primary graphic inside a safe zone circle).

Adaptive Android App Configurations (Mobile & TV):
1. Mobile Launcher Icon (Adaptive):
   - Generate both Foreground and Background layers as transparent PNGs.
   - Dimensions: 108dp x 108dp (total size 512x512 px at high density), keeping the primary logo inside the safe zone (central 72dp / 340px circle).
   - Generate all mipmap asset dimensions:
     - mdpi: 48x48 px
     - hdpi: 72x72 px
     - xhdpi: 96x96 px
     - xxhdpi: 144x144 px
     - xxxhdpi: 192x192 px
2. Android TV App Icon & Banner:
   - TV Launcher Icon: 320x180 px landscape banner (opaque background PNG).
   - TV Adaptive Icon: Foreground (transparent PNG) and Background (opaque PNG) layers sized at 320x180 px.
```
