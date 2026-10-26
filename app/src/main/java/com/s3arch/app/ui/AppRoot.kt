package com.s3arch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.s3arch.app.ui.components.LogoMark
import com.s3arch.app.ui.components.VpnConsentEffect
import com.s3arch.app.ui.theme.S3ARCHTheme
import com.s3arch.app.ui.screens.DownloadsScreen
import com.s3arch.app.ui.screens.SearchScreen
import com.s3arch.app.ui.screens.SettingsScreen
import com.s3arch.app.ui.screens.SetupScreen

@Composable
fun AppRoot(vm: S3archViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Bascule LAN <-> VPN a chaque retour au premier plan, et coupe le tunnel quand
    // l'application passe en arriere-plan (le reste du telephone n'est pas ralenti).
    val lifecycleOwner = ProcessLifecycleOwner.get()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.applyNetworkMode()
                Lifecycle.Event.ON_STOP -> vm.onAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.loaded) {
        if (state.loaded) vm.applyNetworkMode()
    }

    LaunchedEffect(state.message) {
        val text = state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text, withDismissAction = true)
            vm.clearMessage()
        }
    }

    S3ARCHTheme(dynamicColor = state.settings.dynamicColor) {
        VpnConsentEffect(state, vm)
        when {
            !state.loaded -> SplashScreen()
            !state.settings.onboardingDone -> SetupScreen(
                state = state,
                vm = vm,
                snackbarHostState = snackbarHostState,
                onFinish = { vm.finishOnboarding() },
            )
            else -> MainScaffold(state = state, vm = vm, snackbarHostState = snackbarHostState)
        }
    }
}

@Composable
private fun MainScaffold(
    state: UiState,
    vm: S3archViewModel,
    snackbarHostState: SnackbarHostState,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val activeDownloads = state.tasks.values.count { !it.isDone && !it.isError }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    label = { Text("Recherche") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    label = { Text(if (activeDownloads > 0) "Transferts ($activeDownloads)" else "Transferts") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Réglages") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                0 -> SearchScreen(state, vm)
                1 -> DownloadsScreen(state, vm)
                else -> SettingsScreen(state, vm)
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(64)
            Spacer(Modifier.height(18.dp))
            Text("S3ARCH", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(22.dp))
            CircularProgressIndicator(modifier = Modifier.height(26.dp))
        }
    }
}
