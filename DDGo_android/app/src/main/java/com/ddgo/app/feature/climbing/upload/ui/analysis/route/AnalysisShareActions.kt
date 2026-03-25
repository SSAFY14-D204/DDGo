package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

internal fun shareAttemptAnalysis(
    context: Context,
    videoUriString: String?,
    shareTitle: String,
    shareText: String
) {
    val streamUri = videoUriString?.let { resolveSharableVideoUri(context, it) }
    val intent = Intent(Intent.ACTION_SEND).apply {
        if (streamUri != null) {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, streamUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    launchShareChooser(
        context = context,
        intent = intent,
        chooserTitle = "시도 분석 결과 공유"
    )
}

internal fun shareChallengeAnalysis(
    context: Context,
    shareTitle: String,
    shareText: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, shareTitle)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    launchShareChooser(
        context = context,
        intent = intent,
        chooserTitle = "챌린지 분석 결과 공유"
    )
}

private fun resolveSharableVideoUri(
    context: Context,
    videoUriString: String
): Uri? {
    val parsed = runCatching { Uri.parse(videoUriString) }.getOrNull() ?: return null
    if (parsed.scheme == "content") {
        return parsed
    }

    val filePath = when {
        parsed.scheme == "file" -> parsed.path
        parsed.scheme.isNullOrBlank() -> videoUriString
        else -> null
    } ?: return null

    val file = File(filePath)
    if (!file.exists()) {
        return null
    }

    return runCatching {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.getOrNull()
}

private fun launchShareChooser(
    context: Context,
    intent: Intent,
    chooserTitle: String
) {
    try {
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "공유할 수 있는 앱을 찾지 못했습니다.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
