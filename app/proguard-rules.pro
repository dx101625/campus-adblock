-dontwarn io.github.libxposed.annotation.**
-keep class com.campus.adblock.** { *; }

# The hook resolves this interface in the TARGET app's ClassLoader. Renaming it
# rewrites Class.forName's string to our APK's private name and breaks direct QR
# resolution. Keep the interface and its callback contract across the boundary.
-keep interface androidx.lifecycle.Observer { *; }
