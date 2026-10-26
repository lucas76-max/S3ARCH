package com.s3arch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.s3arch.app.ui.AppRoot
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.theme.S3ARCHTheme

class MainActivity : ComponentActivity() {

    private val viewModel: S3archViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            S3ARCHTheme {
                AppRoot(viewModel)
            }
        }
    }
}
