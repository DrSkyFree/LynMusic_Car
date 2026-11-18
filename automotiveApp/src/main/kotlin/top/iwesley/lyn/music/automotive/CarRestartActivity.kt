package top.iwesley.lyn.music.automotive

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.content.Intent

/**
 * 「使用设备」切换后的自动重启执行者。
 *
 * 运行在独立进程(:car_restart):先杀掉旧主进程,再以 CLEAR_TASK 重新拉起主入口,
 * 让新的显示模式(竖屏/横屏)与缩放预设完全生效。旧进程被杀后本进程随即被系统回收。
 */
class CarRestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val oldPid = intent.getIntExtra(EXTRA_OLD_PID, -1)
        if (oldPid != -1 && oldPid != Process.myPid()) {
            Process.killProcess(oldPid)
        }
        val mainIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MAIN_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_MAIN_INTENT)
        }
        if (mainIntent != null) {
            startActivity(mainIntent)
        }
        finish()
    }

    companion object {
        const val EXTRA_OLD_PID = "extra.old_pid"
        const val EXTRA_MAIN_INTENT = "extra.main_intent"
    }
}
