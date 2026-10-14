package com.iread.novel

import android.os.Bundle
import android.os.Build
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_IRead)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Native splash exits as soon as the first app frame is ready. No timer or keep condition.
            splashScreen.setOnExitAnimationListener { splash ->
                if (ValueAnimator.areAnimatorsEnabled()) {
                    splash.animate().alpha(0f).setDuration(150L)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { splash.remove() }.start()
                } else {
                    splash.remove()
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            IReadApp()
        }
    }
}
