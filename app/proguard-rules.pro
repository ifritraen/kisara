-dontobfuscate

# Host classes must preserve their public/protected ABI for external extension APKs
-keep class eu.kanade.** { *; }
-keep class tachiyomi.** { *; }
-keep class mihon.** { *; }

# Extension API Contracts for Manga, Anime, and Novel
-keep class eu.kanade.tachiyomi.source.** { *; }
-keep class eu.kanade.tachiyomi.animesource.** { *; }
-keep class eu.kanade.tachiyomi.novelsource.** { *; }
-keep class eu.kanade.tachiyomi.source.novel.** { *; }
-keep class tachiyomi.domain.source.** { *; }
-keep class tachiyomi.domain.chapter.** { *; }
-keep class tachiyomi.domain.manga.** { *; }
-keep class tachiyomi.domain.entries.anime.** { *; }
-keep class tachiyomi.domain.entries.novel.** { *; }
-keep class tachiyomi.domain.history.novel.** { *; }
-keep class tachiyomi.domain.updates.novel.** { *; }
-keep class tachiyomi.domain.category.novel.** { *; }
-keep class tachiyomi.domain.category.anime.** { *; }

# Keep Kotatsu Parsers package intact for sideloaded jars
-keep class org.koitharu.kotatsu.parsers.** { *; }

# Keep common dependencies used in extensions
-keep class androidx.preference.** { public protected *; }
-keep class kotlin.** { public protected *; }
-keep class kotlinx.coroutines.** { public protected *; }
-keep class kotlinx.serialization.** { public protected *; }
-keep class kotlin.time.** { public protected *; }
-keep class okhttp3.** { public protected *; }
-keep class okio.** { public protected *; }
-keep class org.jsoup.** { public protected *; }
-keep class rx.** { public protected *; }
-keep class uy.kohesive.injekt.** { public protected *; }

# Coroutines Extension ABI & Synthetic Bridges
-keep class kotlinx.coroutines.BuildersKt { *; }
-keep class kotlinx.coroutines.BuildersKt__* { *; }
-keepclassmembers class kotlinx.coroutines.BuildersKt** {
    public static *** runBlocking*(...);
}
-keep class kotlinx.coroutines.CoroutineScopeKt { *; }
-keep class kotlinx.coroutines.DelayKt { *; }
-keep class kotlinx.coroutines.Dispatchers { *; }
-keep class kotlinx.coroutines.YieldKt { *; }
-keep class kotlinx.coroutines.flow.FlowKt { *; }
-keep class kotlinx.coroutines.flow.FlowKt__* { *; }
-keep class kotlinx.coroutines.sync.MutexKt { *; }
-keep class kotlinx.coroutines.sync.SemaphoreKt { *; }
-keep class kotlin.coroutines.jvm.internal.** { *; }
-keep class tachiyomi.core.common.util.lang.RxCoroutineBridgeKt { *; }

# QuickJS & Novel JS Runtime (CRITICAL: Do NOT allow optimization on JNI classes!)
-keep class app.cash.quickjs.** { *; }
-keep class eu.kanade.tachiyomi.extension.novel.runtime.** { *; }
-keep class eu.kanade.tachiyomi.network.JavaScriptEngine { *; }

# Zstd Content-Encoding for Extensions
-keep class com.squareup.zstd.** { *; }
-keep class okhttp3.zstd.** { *; }
-dontwarn com.squareup.zstd.**

# Extension Loaders & ClassLoader
-keep class eu.kanade.tachiyomi.extension.util.ExtensionLoader { *; }
-keep class eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader { *; }
-keep class eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader { *; }

# From extensions-lib
-keep class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { *; }
-keep class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { *; }
-keep class eu.kanade.tachiyomi.network.NetworkHelper { *; }
-keep class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { *; }
-keep class eu.kanade.tachiyomi.network.RequestsKt { *; }
-keep class eu.kanade.tachiyomi.AppInfo { *; }

# Torrent utilities
-keep class eu.kanade.tachiyomi.torrentutils.** { *; }
-keep class aniyomi.core.common.torrent.** { *; }
-dontwarn xyz.secozzi.torrserver.**

# Debug functions
-keep class exh.debug.DebugFunctions { public *; }

##---------------Begin: proguard configuration for RxJava 1.x  ----------
-dontwarn sun.misc.**

-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

-dontnote rx.internal.util.PlatformDependent
##---------------End: proguard configuration for RxJava 1.x  ----------

##---------------Begin: proguard configuration for okhttp  ----------
-keepclasseswithmembers class okhttp3.MultipartBody$Builder { *; }
##---------------End: proguard configuration for okhttp  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.** # core serialization annotations

# kotlinx-serialization-json specific. Add this if you have java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class eu.kanade.**$$serializer { *; }
-keepclassmembers class eu.kanade.** {
    *** Companion;
}
-keepclasseswithmembers class eu.kanade.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class tachiyomi.**$$serializer { *; }
-keepclassmembers class tachiyomi.** {
    *** Companion;
}
-keepclasseswithmembers class tachiyomi.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class exh.**$$serializer { *; }
-keepclassmembers class exh.** {
    *** Companion;
}
-keepclasseswithmembers class exh.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Filter serializer
-keep,includedescriptorclasses class xyz.nulldev.ts.api.http.serializer.**$$serializer { *; }
-keepclassmembers class xyz.nulldev.ts.api.http.serializer.** {
    *** Companion;
}
-keepclasseswithmembers class xyz.nulldev.ts.api.http.serializer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.** {
    <methods>;
}
##---------------End: proguard configuration for kotlinx.serialization  ----------

# === Reactive network: https://github.com/pwittchen/ReactiveNetwork/tree/v0.12.4#proguard-configuration
-dontwarn com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
-dontwarn io.reactivex.functions.Function
-dontwarn rx.internal.util.**
-dontwarn sun.misc.Unsafe

# === Okhttp: https://github.com/square/okhttp/blob/3637fc56f70f87da696847defd311dbfb28e87b5/okhttp/src/main/resources/META-INF/proguard/okhttp3.pro
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*
# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform

# === Okio: https://github.com/square/okio/tree/9b8545e7fa267c9d89753283990f24a35cd69cd6#proguard
-dontwarn okio.**

# === Keep RxAndroid, https://github.com/ReactiveX/RxAndroid/issues/350
-keep class rx.android.** { *; }

# XmlUtil
-keep public enum nl.adaptivity.xmlutil.EventType { *; }

# Firebase
-keep class com.google.firebase.installations.** { *; }
-keep interface com.google.firebase.installations.** { *; }

# Google Drive
-keep class com.google.api.services.** { *; }

# Google OAuth
-keep class com.google.api.client.** { *; }

# SY -->
# SqlCipher
-keepclassmembers class net.zetetic.database.sqlcipher.SQLiteCustomFunction { *; }
-keepclassmembers class net.zetetic.database.sqlcipher.SQLiteConnection { *; }
-keepclassmembers class net.zetetic.database.sqlcipher.SQLiteGlobal { *; }
-keepclassmembers class net.zetetic.database.sqlcipher.SQLiteDebug { *; }
-keepclassmembers class net.zetetic.database.sqlcipher.SQLiteDebug$* { *; }
# SY <--

# KMK -->
# Coil3
-keep class * extends coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * extends coil3.util.FetcherServiceLoaderTarget { *; }

# ONNX Runtime — JNI bridge classes must not be stripped/renamed by R8.
# Release build has isMinifyEnabled=true; without these rules, colorize crashes
# with UnsatisfiedLinkError or NoClassDefFoundError on first use.
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
# KMK <--

# Design library
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }
-keep interface com.google.android.material.** { *; }
-keep public class com.google.android.material.R$* { *; }

-keep class com.hippo.image.** { *; }
-keep interface com.hippo.image.** { *; }

# === Injekt
## From original config: "Attempt to fix: java.lang.NoClassDefFoundError: uy.kohesive.injekt.registry.default.DefaultRegistrar$NOKEY$1"
-keep class uy.kohesive.injekt.** { *; }

# === RxBinding
-dontwarn com.google.auto.value.AutoValue

# === Crashlytics
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**
-keep class com.google.firebase.crashlytics.** { *; }
-keep public class * extends java.lang.Exception  # Optional: Keep custom exceptions.

# === Humanize + Guava: https://github.com/google/guava/wiki/UsingProGuardWithGuava
-dontwarn javax.lang.model.element.Modifier
-keep class org.ocpsoft.prettytime.i18n.**

# Note: We intentionally don't add the flags we'd need to make Enums work.
# That's because the Proguard configuration required to make it work on
# optimized code would preclude lots of optimization, like converting enums
# into ints.

# Throwables uses internal APIs for lazy stack trace resolution
-dontnote sun.misc.SharedSecrets
-keep class sun.misc.SharedSecrets {
  *** getJavaLangAccess(...);
}
-dontnote sun.misc.JavaLangAccess
-keep class sun.misc.JavaLangAccess {
  *** getStackTraceElement(...);
  *** getStackTraceDepth(...);
}

# FinalizableReferenceQueue calls this reflectively
# Proguard is intelligent enough to spot the use of reflection onto this, so we
# only need to keep the names, and allow it to be stripped out if
# FinalizableReferenceQueue is unused.
-keepnames class com.google.common.base.internal.Finalizer {
  *** startFinalizer(...);
}
# However, it cannot "spot" that this method needs to be kept IF the class is.
-keepclassmembers class com.google.common.base.internal.Finalizer {
  *** startFinalizer(...);
}
-keepnames class com.google.common.base.FinalizableReference {
  void finalizeReferent();
}
-keepclassmembers class com.google.common.base.FinalizableReference {
  void finalizeReferent();
}

# Striped64, LittleEndianByteArray, UnsignedBytes, AbstractFuture
-dontwarn sun.misc.Unsafe

# Striped64 appears to make some assumptions about object layout that
# really might not be safe. This should be investigated.
-keepclassmembers class com.google.common.cache.Striped64 {
  *** base;
  *** busy;
}
-keepclassmembers class com.google.common.cache.Striped64$Cell {
  <fields>;
}

-dontwarn java.lang.SafeVarargs

-keep class java.lang.Throwable {
  *** addSuppressed(...);
}

# Futures.getChecked, in both of its variants, is incompatible with proguard.

# Used by AtomicReferenceFieldUpdater and sun.misc.Unsafe
-keepclassmembers class com.google.common.util.concurrent.AbstractFuture** {
  *** waiters;
  *** value;
  *** listeners;
  *** thread;
  *** next;
}
-keepclassmembers class com.google.common.util.concurrent.AtomicDouble {
  *** value;
}
-keepclassmembers class com.google.common.util.concurrent.AggregateFutureState {
  *** remaining;
  *** seenExceptions;
}

# Since Unsafe is using the field offsets of these inner classes, we don't want
# to have class merging or similar tricks applied to these classes and their
# fields. It's safe to allow obfuscation, since the by-name references are
# already preserved in the -keep statement above.
-keep,allowshrinking,allowobfuscation class com.google.common.util.concurrent.AbstractFuture** {
  <fields>;
}

# Futures.getChecked (which often won't work with Proguard anyway) uses this. It
# has a fallback, but again, don't use Futures.getChecked on Android regardless.
-dontwarn java.lang.ClassValue

# MoreExecutors references AppEngine
-dontnote com.google.appengine.api.ThreadManager
-keep class com.google.appengine.api.ThreadManager {
  static *** currentRequestThreadFactory(...);
}
-dontnote com.google.apphosting.api.ApiProxy
-keep class com.google.apphosting.api.ApiProxy {
  static *** getCurrentEnvironment (...);
}

# R8 full mode
 -keepattributes Signature
 -keep class kotlin.coroutines.Continuation { *; }
 -keep class * extends uy.kohesive.injekt.api.TypeReference { *; }
 -keep public class io.requery.android.database.sqlite.SQLiteConnection { *; }

 # Keep apache http client
 -keep class org.apache.http.** { *; }

# Suggested rules
-dontwarn com.oracle.svm.core.annotate.AutomaticFeature
-dontwarn com.oracle.svm.core.annotate.Delete
-dontwarn com.oracle.svm.core.annotate.Substitute
-dontwarn com.oracle.svm.core.annotate.TargetClass
-dontwarn com.oracle.svm.core.configure.ResourcesRegistry
-dontwarn org.graalvm.nativeimage.ImageSingletons
-dontwarn org.graalvm.nativeimage.hosted.Feature$BeforeAnalysisAccess
-dontwarn org.graalvm.nativeimage.hosted.Feature
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.Module
-dontwarn org.graalvm.nativeimage.hosted.RuntimeResourceAccess
-dontwarn org.jspecify.annotations.NullMarked
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
