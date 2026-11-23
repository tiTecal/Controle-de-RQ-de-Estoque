# ==============================================================================
# R8 / ProGuard Anti-Reverse-Engineering Rules - Tecal Almoxarifado
# ==============================================================================

-repackageclasses 'o'
-allowaccessmodification
-overloadaggressively
-optimizationpasses 5

# Oculta nomes de arquivos e pilha original no APK de produção, preservando anotações de Retrofit/Serialization
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Signature,InnerClasses

# Mantém exclusivamente a ponte de serialização kotlinx.serialization (campos usam @SerialName e são ofuscados)
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep class <1>$serializer { *; }
-keepclasseswithmembers class br.com.tecal.almoxarifado.data.model.** {
    static **$Companion Companion;
}
-keepclassmembers class br.com.tecal.almoxarifado.data.model.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class br.com.tecal.almoxarifado.data.model.**$serializer {
    *;
}

# Retrofit interface proxy (anotações HTTP e parâmetros preservados; nomes internos ofuscados)
-keep,allowobfuscation,allowshrinking interface br.com.tecal.almoxarifado.data.remote.BffApiService {
    <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Remove todas as chamadas de Log em builds Release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Suprime anotações compile-time do Google Tink (androidx.security:security-crypto)
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
