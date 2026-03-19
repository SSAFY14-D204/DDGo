package com.ddgo.app.feature.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.components.keyboardAwareBottomPadding
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
fun RegisterPasswordScreen(viewModel: AuthViewModel, onRegComplete: () -> Unit, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                Toast.makeText(context, "\uD68C\uC6D0\uAC00\uC785\uC774 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
                onRegComplete()
            }

            is AuthUiState.Error -> {
                Toast.makeText(context, (uiState as AuthUiState.Error).message, Toast.LENGTH_SHORT).show()
                viewModel.resetUiState()
            }

            else -> {}
        }
    }

    SafeAreaScreen(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp)
            .keyboardAwareBottomPadding(),
        containerColor = Color.White,
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
                text = "\uBE44\uBC00\uBC88\uD638\uB97C \uC815\uD655\uD558\uAC8C \uC785\uB825\uD574\uC8FC\uC138\uC694",
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "\uBE44\uBC00\uBC88\uD638",
                style = TextStyle(
                    fontFamily = PretendardFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E232C)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = viewModel.password,
                onValueChange = viewModel::updatePassword,
                placeholder = { Text("\uBE44\uBC00\uBC88\uD638", color = Color(0xFF8391A1)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color(0xFF1DA1F2),
                    unfocusedIndicatorColor = Color(0xFF1DA1F2),
                ),
                trailingIcon = {
                    Text("~", color = Color(0xFF64788D))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "\u2715 \uC601\uBB38/\uC22B\uC790/\uD2B9\uC218\uBB38\uC790 2\uAC00\uC9C0 \uC774\uC0C1 \uC870\uD569 (8~20\uC790)",
                style = TextStyle(
                    color = Color(0xFFFF3B30),
                    fontSize = 12.sp,
                    fontFamily = PretendardFamily
                )
            )
        }

        Button(
            onClick = { viewModel.register() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64788D)),
            enabled = uiState != AuthUiState.Loading && viewModel.password.isNotBlank()
        ) {
            if (uiState == AuthUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "\uB2E4\uC74C",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = PretendardFamily
                )
            }
        }
    }
}
