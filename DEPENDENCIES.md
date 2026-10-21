# Gradle setup

app/build.gradle.kts:
  - change minSdk from 24 to 26 (java.time is used)
  - add to dependencies { }:

    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

OkHttp is no longer needed (the fetch now runs inside a WebView).
No KSP / Room is needed.
