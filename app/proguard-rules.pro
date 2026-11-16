# Aturan pengecilan kode untuk rakitan rilis.

# ML Kit (tulisan tangan dan deteksi bahasa) memanggil sebagian kelasnya dari kode native
# dan lewat refleksi; biarkan utuh supaya pengenal tetap berjalan.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.odml.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Pustaka opsional yang memang tidak dipakai.
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.auto.value.**
-dontwarn okhttp3.**
-dontwarn okio.**
