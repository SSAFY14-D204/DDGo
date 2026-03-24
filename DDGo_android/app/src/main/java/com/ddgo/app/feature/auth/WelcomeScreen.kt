package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.ddgo.app.R
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun AuthLandingScreen(onRegisterClick: () -> Unit, onLoginClick: () -> Unit) {
    SafeAreaScreen(
        modifier = Modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF8667FF).copy(alpha = 0.58f),
                        Color(0xFFD7CBFF).copy(alpha = 0.28f),
                        Color.White,
                        Color.White
                    ),
                    start = Offset(320f, -48f),
                    end = Offset(72f, 520f)
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DdgoMascotMark()
            Spacer(modifier = Modifier.height(18.dp))
            DdgoKoreanWordmark(fontSize = 68.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = AuthStrings.WelcomeDescription,
                color = Color(0xFF65676C),
                fontFamily = PretendardFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DdgoPrimaryButton(
                text = AuthStrings.WelcomeRegister,
                onClick = onRegisterClick,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${AuthStrings.WelcomeLoginQuestion} ",
                    color = DdgoColorTokens.TextSecondary,
                    fontFamily = PretendardFamily,
                    fontSize = 15.sp
                )
                Text(
                    text = AuthStrings.WelcomeLoginAction,
                    modifier = Modifier.clickable(onClick = onLoginClick),
                    color = DdgoColorTokens.BrandBlue,
                    fontFamily = PretendardFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun DdgoMascotMark() {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(R.raw.main_dd_icon)
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = "디디고 로고",
        modifier = Modifier
            .width(153.dp)
            .height(99.dp)
    )
}
