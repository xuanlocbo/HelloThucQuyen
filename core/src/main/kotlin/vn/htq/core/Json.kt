package vn.htq.core

/** Small dependency-free JSON reader shared by the JVM contract tests and Android. */
typealias Obj = Map<String, Any?>
typealias Arr = List<Any?>
@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Obj = this as? Map<String, Any?> ?: error("Expected object")
fun Any?.arr(): Arr = this as? List<Any?> ?: error("Expected array")
fun Any?.str(): String = this as? String ?: error("Expected string")
fun Obj.s(k: String): String = get(k).str()
fun Obj.optional(k: String): String? = get(k) as? String
fun Obj.o(k: String): Obj = get(k).obj()
fun Obj.a(k: String): Arr = get(k).arr()
fun Obj.strings(k: String): List<String> = a(k).map { it.str() }
fun Obj.b(k: String): Boolean = get(k) == true
fun Obj.n(k: String): Int = (get(k) as? Number)?.let { n -> n.toInt().also { require(n.toDouble() == it.toDouble()) } } ?: error("Expected integer: $k")
object Json {
    fun parse(text: String): Any? = Parser(text).parse()
    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"'); for (c in value) when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                else -> if (c.code < 32) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
            }; append('"')
        }
        is Boolean -> value.toString()
        is Number -> value.toString().also { require(value.toDouble().isFinite()) }
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { encode(it.key.str()) + ":" + encode(it.value) }
        is List<*> -> value.joinToString(",", "[", "]") { encode(it) }
        else -> error("Unsupported JSON value")
    }
    private class Parser(private val t: String) {
        private var p = 0
        private fun fail(): Nothing = error("Invalid JSON at offset $p")
        private fun ws() { while (p < t.length && t[p] in " \n\r\t") p++ }
        private fun take(c: Char): Boolean { ws(); if (p < t.length && t[p] == c) { p++; return true }; return false }
        private fun expect(c: Char) { if (!take(c)) fail() }
        fun parse(): Any? { val v = value(0); ws(); if (p != t.length) fail(); return v }
        private fun value(depth: Int): Any? {
            if (depth > 128) fail(); ws(); if (p >= t.length) fail()
            return when (t[p]) {
                '{' -> { p++; val m = linkedMapOf<String, Any?>(); if (!take('}')) { do { ws(); val k = string(); expect(':'); if (m.containsKey(k)) fail(); m[k] = value(depth + 1) } while (take(',')); expect('}') }; m }
                '[' -> { p++; val a = arrayListOf<Any?>(); if (!take(']')) { do { a.add(value(depth + 1)) } while (take(',')); expect(']') }; a }
                '"' -> string(); 't' -> literal("true", true); 'f' -> literal("false", false); 'n' -> literal("null", null)
                '-', in '0'..'9' -> number(); else -> fail()
            }
        }
        private fun literal(s: String, v: Any?): Any? { if (!t.startsWith(s, p)) fail(); p += s.length; return v }
        private fun string(): String {
            if (p >= t.length || t[p++] != '"') fail()
            val b = StringBuilder()
            while (p < t.length) {
                val c = t[p++]
                when {
                    c == '"' -> return b.toString()
                    c.code < 32 -> fail()
                    c != '\\' -> b.append(c)
                    else -> {
                        if (p >= t.length) fail()
                        when (val e = t[p++]) {
                            '"', '\\', '/' -> b.append(e); 'b' -> b.append('\b'); 'f' -> b.append('\u000c'); 'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t')
                            'u' -> { if (p + 4 > t.length) fail(); b.append((t.substring(p, p + 4).toIntOrNull(16) ?: fail()).toChar()); p += 4 }
                            else -> fail()
                        }
                    }
                }
            }; fail()
        }
        private fun number(): Number {
            val start = p
            if (t[p] == '-') p++
            if (p >= t.length) fail()
            if (t[p] == '0') p++ else { if (t[p] !in '1'..'9') fail(); while (p < t.length && t[p] in '0'..'9') p++ }
            if (p < t.length && t[p] == '.') { p++; val q = p; while (p < t.length && t[p] in '0'..'9') p++; if (p == q) fail() }
            if (p < t.length && (t[p] == 'e' || t[p] == 'E')) { p++; if (p < t.length && t[p] in "+-") p++; val q = p; while (p < t.length && t[p] in '0'..'9') p++; if (p == q) fail() }
            val s = t.substring(start, p); return s.toLongOrNull() ?: (s.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fail())
        }
    }
}
