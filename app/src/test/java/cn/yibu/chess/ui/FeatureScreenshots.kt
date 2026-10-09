package cn.yibu.chess.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import cn.yibu.chess.BuildConfig
import java.io.File

internal fun ComposeContentTestRule.featureScreenshot(name: String) = runOnIdle {
    val root = WindowInspector.getGlobalWindowViews().last()
    val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bitmap))
    val output = File("../artifacts/ui-${BuildConfig.VERSION_NAME}/$name.png").apply { parentFile?.mkdirs() }
    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}
