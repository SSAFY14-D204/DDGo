package com.ddgo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private var passwordResetDeepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passwordResetDeepLink = extractPasswordResetDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            DDGoTheme {
                NavGraph(
                    passwordResetDeepLink = passwordResetDeepLink,
                    onPasswordResetDeepLinkConsumed = {
                        passwordResetDeepLink = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        passwordResetDeepLink = extractPasswordResetDeepLink(intent)
    }

    private fun extractPasswordResetDeepLink(intent: Intent?): String? {
        val data = intent?.data ?: return null
        val path = data.path.orEmpty()
        val host = data.host.orEmpty()

        val isPasswordResetLink =
            host == "password-reset" || path == "/reset-password" || path.endsWith("/reset-password")

        return if (intent.action == Intent.ACTION_VIEW && isPasswordResetLink) {
            data.toString()
        } else {
            null
        }
    }
}
