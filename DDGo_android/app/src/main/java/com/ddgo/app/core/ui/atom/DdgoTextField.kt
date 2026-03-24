package com.ddgo.app.core.ui.atom

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.tokens.DdgoShapeTokens

enum class DdgoFieldState {
    Default,
    Error,
    Disabled
}

@Composable
fun DdgoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
    state: DdgoFieldState = DdgoFieldState.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val enabled = state != DdgoFieldState.Disabled
    val isError = state == DdgoFieldState.Error

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        shape = DdgoShapeTokens.Input,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        isError = isError,
        visualTransformation = visualTransformation,
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DdgoColorTokens.TextHint
                )
            }
        },
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DdgoColorTokens.TextSecondary
                )
            }
        },
        trailingIcon = trailingText?.let { text ->
            {
                Text(
                    text = text,
                    modifier = Modifier.padding(end = 6.dp),
                    color = DdgoColorTokens.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        supportingText = supportingText?.let { text ->
            {
                Text(
                    text = text,
                    color = if (isError) DdgoColorTokens.Error else DdgoColorTokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DdgoColorTokens.Surface,
            unfocusedContainerColor = DdgoColorTokens.Surface,
            disabledContainerColor = DdgoColorTokens.SurfaceMuted,
            errorContainerColor = DdgoColorTokens.Surface,
            focusedBorderColor = DdgoColorTokens.BrandBlue,
            unfocusedBorderColor = DdgoColorTokens.Border,
            disabledBorderColor = DdgoColorTokens.Border,
            errorBorderColor = DdgoColorTokens.Error,
            focusedTextColor = DdgoColorTokens.TextPrimary,
            unfocusedTextColor = DdgoColorTokens.TextPrimary,
            disabledTextColor = DdgoColorTokens.TextSecondary,
            errorTextColor = DdgoColorTokens.TextPrimary,
            focusedLabelColor = DdgoColorTokens.BrandBlue,
            cursorColor = DdgoColorTokens.BrandBlue
        )
    )
}
