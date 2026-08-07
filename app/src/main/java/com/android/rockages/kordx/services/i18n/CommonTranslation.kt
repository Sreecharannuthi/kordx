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
