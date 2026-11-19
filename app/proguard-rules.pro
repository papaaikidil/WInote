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

# PdfBox (teks dan formulir PDF): beberapa kelas dimuat lewat refleksi; dekoder JPEG 2000 opsional tidak dipakai.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
