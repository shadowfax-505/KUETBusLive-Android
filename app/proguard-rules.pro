# R8 rules for the release build.

# Keep line numbers so crash reports point at the right line, and hide file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# App models are read from and written to Firestore by hand (data/Mappers.kt), but keep
# them whole so field names stay readable in crash reports and nothing is stripped by mistake.
-keep class io.github.shadowfax505.kuetbuslive.model.** { *; }

# Kotlin coroutines and Firebase ship their own consumer rules; these quiet optional references.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
