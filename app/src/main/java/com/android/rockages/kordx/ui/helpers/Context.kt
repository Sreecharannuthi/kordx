package com.android.rockages.kordx.ui.helpers

import androidx.navigation.NavHostController
import com.android.rockages.kordx.MainActivity
import com.android.rockages.kordx.KordX

data class ViewContext(
 val kordx: KordX,
 val activity: MainActivity,
 val navController: NavHostController,
) {
 companion object {
 fun <T> parameterizedFn(fn: (ViewContext) -> T) = fn
 }
}
