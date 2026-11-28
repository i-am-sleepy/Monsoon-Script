package androidx.autofill.inline.v1;

import android.app.PendingIntent;
import android.app.slice.Slice;
import android.graphics.drawable.Icon;
import androidx.autofill.inline.UiVersions;

// Stand-in for the compile check only (tools/compile_check.sh): signatures as in the androidx.autofill 1.3.0 reference. The real library comes from Gradle.
public final class InlineSuggestionUi {
    public static Content.Builder newContentBuilder(PendingIntent attributionIntent) { return null; }

    public static Style.Builder newStyleBuilder() { return null; }

    public static final class Content implements UiVersions.Content {
        public Slice getSlice() { return null; }

        public static final class Builder {
            public Builder setContentDescription(CharSequence value) { return this; }
            public Builder setTitle(CharSequence value) { return this; }
            public Builder setSubtitle(CharSequence value) { return this; }
            public Builder setStartIcon(Icon value) { return this; }
            public Builder setEndIcon(Icon value) { return this; }
            public Content build() { return null; }
        }
    }

    public static final class Style implements UiVersions.Style {
        public static final class Builder {
            public Style build() { return null; }
        }
    }
}
