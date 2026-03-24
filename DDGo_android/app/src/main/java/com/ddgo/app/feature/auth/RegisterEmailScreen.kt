package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.atom.DdgoFieldState
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoTextField
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun RegisterEmailScreen(viewModel: AuthViewModel, onNext: () -> Unit, onBack: () -> Unit = {}) {
    val errorMessage = viewModel.errorMessage

    LaunchedEffect(Unit) {
        viewModel.clearErrorState()
    }

    SafeAreaScreen(
        modifier = Modifier
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp)
            .keyboardAwareBottomPadding(),
        applyBottomInset = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "\uB4A4\uB85C\uAC00\uAE30")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "\uC27D\uAC8C \uAC00\uC785\uD558\uACE0\n\uAC04\uD3B8\uD558\uAC8C \uB85C\uADF8\uC778\uD558\uC138\uC694.",
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "\uC774\uBA54\uC77C",
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            DdgoTextField(
                value = viewModel.username,
                onValueChange = viewModel::updateUsername,
                placeholder = "\uC774\uBA54\uC77C",
                modifier = Modifier.fillMaxWidth(),
                state = if (errorMessage != null) DdgoFieldState.Error else DdgoFieldState.Default,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                AuthInlineErrorMessage(message = message)
            }
        }

        DdgoPrimaryButton(
            text = "\uB2E4\uC74C",
            onClick = {
                if (viewModel.validateUsernameStep() == null) {
                    onNext()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}
