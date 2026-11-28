package androidx.annotation;

// Stand-in for the compile check only (tools/compile_check.sh); the real androidx.annotation comes from Gradle.
public @interface RequiresApi {
    int value() default 1;
    int api() default 1;
}
