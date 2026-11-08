# Crimson source (stage 2)

Apply in your Codespace, from the repo root:

    unzip -o crimson-source.zip
    rm -f .github/workflows/ci.yml .github/workflows/release.yml
    git add -A && git commit -m "Crimson stage 2" && git push

Then run "Build Crimson" in the Actions tab and download the APK from Artifacts.

New in this stage (all under app/src/main/java/org/mozilla/fenix/crimson/ and assets/extensions/crimson-tools/):
- Startup animation (CrimsonSplash.kt): dot falls onto the C, C vanishes top to bottom, dot drops away; toggle in Settings > Crimson
- Accent color picker with auto gradient (CrimsonColorPreference.kt)
- Dark mode for websites + Developer panel (built-in extension "Crimson Tools", driven by CrimsonTools.kt)

If the build fails, paste the last 40 lines of the error from the failed step.
