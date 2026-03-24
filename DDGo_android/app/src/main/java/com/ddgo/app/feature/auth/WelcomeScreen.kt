package com.ddgo.app.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoTextButton
import com.ddgo.app.core.ui.atom.DdgoTextButtonTone
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun AuthLandingScreen(onRegisterClick: () -> Unit, onLoginClick: () -> Unit) {
    SafeAreaScreen(
        modifier = Modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DdgoColorTokens.SurfaceTint,
                        DdgoColorTokens.Surface
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DdgoMascotBadge()
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "\uCD94\uB77D\uC758 \uB370\uC774\uD130\uB97C \uBC14\uAFB8\uB294",
                fontSize = 16.sp,
                fontFamily = PretendardFamily
            )
            Spacer(modifier = Modifier.height(10.dp))
            DdgoKoreanWordmark(fontSize = 60.sp)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DdgoPrimaryButton(
                text = "\uC2DC\uC791\uD558\uAE30",
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\uC774\uBBF8 \uACC4\uC815\uC774 \uC788\uB098\uC694? ",
                    color = DdgoColorTokens.TextSecondary,
                    fontFamily = PretendardFamily
                )
                DdgoTextButton(
                    text = "\uB85C\uADF8\uC778",
                    onClick = onLoginClick,
                    tone = DdgoTextButtonTone.Primary
                )
            }
        }
    }
}

@Composable
private fun DdgoMascotBadge(size: Dp = 132.dp) {
    Card(
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            DdgoColorTokens.BrandBlue,
                            DdgoColorTokens.BrandGradientStart
                        )
                    ),
                    shape = RoundedCornerShape(36.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_ddgo_mascot),
                contentDescription = "\uB514\uB514 \uCE90\uB9AD\uD130",
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}
