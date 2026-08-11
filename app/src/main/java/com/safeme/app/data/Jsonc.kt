package com.safeme.app.data

/**
 * Minimal JSONC (JSON with comments) support for backup files.
 *
 * The app only ever writes strict JSON, but backup files are meant to be
 * human-editable, so the importer also accepts the two JSONC conveniences:
 *
 *  - line comments and block comments (stripped before parsing), and
 *  - trailing commas inside objects/arrays.
 *
 * Everything outside string literals is processed; anything inside a `"..."`
 * string (including comment markers or trailing commas) is preserved
 * verbatim, so e.g. a keyword containing `//` survives a round trip.
 */
object Jsonc {

    /**
     * Converts a JSONC document into strict JSON that [org.json.JSONObject]
     * can parse. Returns the cleaned text; callers still need to handle the
     * case where the input is not JSON at all (parse will fail).
     */
    fun toStrictJson(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]
            when {
                // String literal — copy verbatim including escapes.
                c == '"' -> {
                    val start = i
                    i++
                    while (i < n) {
                        val sc = raw[i]
                        i++
                        if (sc == '\\' && i < n) {
                            i++ // skip escaped char
                        } else if (sc == '"') {
                            break
                        }
                    }
                    out.append(raw, start, i)
                }
                // Line comment.
                c == '/' && i + 1 < n && raw[i + 1] == '/' -> {
                    i += 2
                    while (i < n && raw[i] != '\n' && raw[i] != '\r') i++
                }
                // Block comment.
                c == '/' && i + 1 < n && raw[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(raw[i] == '*' && raw[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                }
                // Trailing comma (only when followed by a closing bracket).
                c == ',' -> {
                    var j = i + 1
                    while (j < n && raw[j].isWhitespace()) j++
                    if (j < n && (raw[j] == '}' || raw[j] == ']')) {
                        i++ // drop the comma
                    } else {
                        out.append(c)
                        i++
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }
}
