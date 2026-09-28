# Consumer rules for the Junkcoin MWEB library.
# gomobile binds Go types reflectively through JNI — keep generated classes.
-keep class go.** { *; }
-keep class xyz.junkcoin.mweb.** { *; }
-keepclasseswithmembers class xyz.junkcoin.mweb.** { *; }
