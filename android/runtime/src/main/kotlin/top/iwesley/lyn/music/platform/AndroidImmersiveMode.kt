package top.iwesley.lyn.music.platform

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 车机全屏(沉浸)模式:隐藏系统状态栏/导航栏,Android 7.0 ~ 最新通用。
 *
 * WindowInsetsControllerCompat 会在 API 24-29 内部映射为 IMMERSIVE_STICKY 等 systemUiVisibility,
 * API 30+ 走 WindowInsetsController;配合 setDecorFitsSystemWindows(false) 让内容铺满全屏。
 *
 * 已知边界:个别车机 ROM 用独立 overlay 窗口自绘状态栏,任何应用 API 都无法移除该窗口,
 * 此时只能做到内容铺满,需在 ROM 设置中关闭 overlay 状态栏。
 */
fun applyCarImmersiveMode(window: Window, immersive: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, !immersive)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (immersive) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
