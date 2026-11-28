package androidx.autofill.inline;

import android.app.slice.Slice;
import android.os.Bundle;
import java.util.List;

// Stand-in for the compile check only (tools/compile_check.sh): signatures as in the androidx.autofill 1.3.0 reference. The real library comes from Gradle.
public final class UiVersions {
    public static final String INLINE_UI_VERSION_1 = "androidx.autofill.inline.ui.version:v1";

    public static List<String> getVersions(Bundle versionedBundle) { return null; }

    public static StylesBuilder newStylesBuilder() { return null; }

    public interface Style {}

    public interface Content { Slice getSlice(); }

    public static final class StylesBuilder {
        public StylesBuilder addStyle(Style style) { return this; }
        public Bundle build() { return null; }
    }
}
