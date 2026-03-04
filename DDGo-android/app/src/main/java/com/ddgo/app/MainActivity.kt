package com.ddgo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ddgo.app.core.ui.theme.DDGoTheme
import com.ddgo.app.navigation.NavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱의 유일한 Activity.
 *
 * @AndroidEntryPoint: Hilt가 이 Activity에 의존성을 주입할 수 있게 합니다.
 * NavGraph를 통해 모든 화면의 네비게이션을 관리합니다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DDGoTheme {
                NavGraph()
            }
        }
    }
}