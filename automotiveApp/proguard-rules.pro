# LynMusic_Car (automotiveApp) R8 rules — M6 size milestone.
# Release enables isMinifyEnabled/isShrinkResources; these rules keep the reflective
# and JNI surfaces that R8 cannot infer: Room KMP + bundled SQLite driver,
# androidx.media3, jaudiotagger (reflection), kotlinx.coroutines/serialization.

# --- Generic reflective surface -----------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, AnnotationDefault, Signature, InnerClasses, EnclosingMethod

# Native methods must keep their names for JNI registration (bundled SQLite driver).
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Room KMP (androidx.room 2.8.x + androidx.sqlite:sqlite-bundled) ----------------------
# Room instantiates generated *_Impl databases and reflects over annotated columns.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class androidx.room.** {
    *;
}
-keep @androidx.room.Entity class * {
    *;
}
-keep @androidx.room.Dao interface * {
    *;
}
-keep @androidx.room.Database class * {
    *;
}
-dontwarn androidx.room.paging.**

# --- androidx.media3 (ExoPlayer/session/datasource/extractor) ------------------------------
-keep class androidx.media3.** {
    *;
}
-dontwarn androidx.media3.**

# --- jaudiotagger (net.jthink:jaudiotagger, reflection-based tag parsing) ------------------
-keep class org.jaudiotagger.** {
    *;
}
-dontwarn org.jaudiotagger.**

# --- kotlinx.coroutines --------------------------------------------------------------------
# Preserve service-loader factory names and the volatile fields documented by
# kotlinx-coroutines for R8 builds.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- kotlinx.serialization ------------------------------------------------------------------
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer {
    *;
}

# --- sardine-android / simple-xml (WebDAV sources) ------------------------------------------
# simple-xml persists WebDAV response DTOs via annotation reflection; the platform's
# kxml2 backs the XmlPullParser at runtime (xpp3 program classes are excluded in Gradle).
-keep class com.thegrizzlylabs.sardineandroid.** {
    *;
}
-keep class org.simpleframework.xml.** {
    *;
}
-keepclassmembers class * {
    @org.simpleframework.xml.Root <init>(...);
    @org.simpleframework.xml.Attribute <fields>;
    @org.simpleframework.xml.Element <fields>;
    @org.simpleframework.xml.ElementList <fields>;
    @org.simpleframework.xml.ElementMap <fields>;
    @org.simpleframework.xml.Text <fields>;
}
-dontwarn javax.xml.stream.**
-dontwarn org.joda.time.**

# --- SMB / RPC / WebDAV stack optional JVM paths --------------------------------------------
# smbj (+ r7 dcerpc), MBassador and ktor reference desktop-only JDK APIs that never exist
# on Android; the code paths using them are optional (GSS/SPNEGO auth, EL filters,
# debugger detection). Rules per AGP missing_rules.txt.
-dontwarn java.lang.management.**
-dontwarn java.rmi.**
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
