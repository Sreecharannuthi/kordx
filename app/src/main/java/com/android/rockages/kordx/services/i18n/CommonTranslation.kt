package com.android.rockages.kordx.services.i18n

@Suppress("Unused", "ConstPropertyName", "FunctionName")
object CommonTranslation {
 const val SomethingWentHorriblyWrong = "Something went horribly wrong!"
 const val System = "System"
 const val Home = "Home"
 const val Details = "Details"
 const val ReportAnIssue = "Report an issue"

 fun ErrorX(x: String) = "Error: $x"
 fun SomethingWentHorriblyWrongErrorX(x: String) =
 "$SomethingWentHorriblyWrong (${ErrorX(x)})"
}

/**
 * Substitutes `{x}`, `{y}`, `{z}`, … placeholders with the supplied arguments.
 *
 * The placeholder name is interpreted positionally: `{x}` maps to [args][0],
 * `{y}` to [args][1], `{z}` to [args][2], and so on. This matches the naming
 * convention used in KordX's TOML/JSON translation assets and supports locales
 * that reorder placeholders (e.g. Hindi/Telugu/Japanese "{y} of {x}"). Any
 * placeholder that cannot be resolved is left unchanged, which is safer than
 * crashing when a translation accidentally drops a placeholder.
 */
fun String.substitutePlaceholders(vararg args: String): String {
 if (args.isEmpty() || !contains('{')) return this
 val regex = "\\{([a-zA-Z][a-zA-Z0-9]*)\\}".toRegex()
 return replace(regex) { match ->
  val name = match.groupValues[1]
  val index = name.lowercase()[0] - 'x'
  if (index in 0..args.lastIndex) args[index] else match.value
 }
}
