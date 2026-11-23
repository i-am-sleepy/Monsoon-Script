// Runs the @Test methods of the classes named on the command line (see tools/local_check.sh). Prints ok / FAIL per test.
import org.junit.Test
fun main(args: Array<String>) {
    var failed = 0; var ran = 0
    for (cn in args) {
        val c = Class.forName(cn); val o = c.getDeclaredConstructor().newInstance()
        for (m in c.declaredMethods.sortedBy { it.name }) {
            if (m.getAnnotation(Test::class.java) == null) continue
            ran++
            try { m.invoke(o); println("ok    ${c.simpleName}.${m.name}") }
            catch (e: java.lang.reflect.InvocationTargetException) { failed++; println("FAIL  ${c.simpleName}.${m.name}: ${e.targetException}") }
        }
    }
    println("ran $ran, failed $failed")
    if (failed > 0) System.exit(1)
}
