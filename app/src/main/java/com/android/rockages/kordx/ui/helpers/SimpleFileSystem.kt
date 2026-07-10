package com.android.rockages.kordx.ui.helpers

import com.android.rockages.kordx.core.utils.SimpleFileSystem
import com.android.rockages.kordx.core.utils.SimplePath

fun SimpleFileSystem.Folder.navigateToFolder(path: SimplePath): SimpleFileSystem.Folder? {
 var folder: SimpleFileSystem.Folder? = this
 path.parts.forEach { x ->
 folder = folder?.let {
 val child = it.children[x]
 child as? SimpleFileSystem.Folder
 }
 }
 return folder
}
