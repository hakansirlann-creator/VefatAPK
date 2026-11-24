package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthHelper
import com.example.data.LocalSnapshotCache
import com.example.data.VefatRepository
import com.example.ui.VefatViewModel
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SignInWelcomeScreen
import com.example.ui.theme.BatmanVefatTheme
import com.example.util.VefatNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

private sealed interface AppRoute {
    data object Home : AppRoute
    data class Detail(val recordId: String) : AppRoute
    data object Admin : AppRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        VefatNotificationHelper.ensureChannel(this)
        setContent {
            BatmanVefatTheme {
                BatmanVefatApp()
            }
        }
    }
}

@Composable
fun BatmanVefatApp() {
    val auth = Firebase.auth
    val currentUser by AuthHelper.authStateFlow(auth)
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var freshGoogleSignIn by remember { mutableStateOf(false) }

    val user = currentUser
    if (user == null) {
        SignInWelcomeScreen(
            onAuthSuccess = { freshGoogleSignIn = true }
        )
    } else {
        key(user.uid) {
            val isFresh = remember(user.uid) {
                val flag = freshGoogleSignIn
                freshGoogleSignIn = false
                flag
            }
            AuthenticatedContent(
                currentUserId = user.uid,
                currentUserEmail = user.email,
                currentUserDisplayName = user.displayName,
                isFreshGoogleSignIn = isFresh
            )
        }
    }
}

@Composable
private fun AuthenticatedContent(
    currentUserId: String,
    currentUserEmail: String?,
    currentUserDisplayName: String?,
    isFreshGoogleSignIn: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: VefatViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VefatViewModel(
                    repository = VefatRepository(db, Firebase.auth),
                    localCache = LocalSnapshotCache(app),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = currentUserDisplayName,
                    isFreshGoogleSignIn = isFreshGoogleSignIn
                )
            }
        }
    )

    var isForeground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isForeground = true
                    viewModel.setPresence(true)
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    isForeground = false
                    viewModel.setPresence(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPresence(false)
        }
    }

    LaunchedEffect(isForeground, viewModel) {
        while (isForeground) {
            delay(45_000L)
            if (isForeground) {
                viewModel.setPresence(true)
            }
        }
    }

    DisposableEffect(context, viewModel) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeCaps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val initiallyOnline = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        viewModel.setOfflineStatus(!initiallyOnline)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModel.setOfflineStatus(false)
            }

            override fun onLost(network: Network) {
                val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                val stillOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                viewModel.setOfflineStatus(!stillOnline)
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose {
            runCatching { cm?.unregisterNetworkCallback(callback) }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingNotification by viewModel.systemNotificationEvent.collectAsStateWithLifecycle()
    var currentRoute by remember { mutableStateOf<AppRoute>(AppRoute.Home) }

    LaunchedEffect(pendingNotification) {
        val bildirim = pendingNotification
        if (bildirim != null) {
            VefatNotificationHelper.showVefatNotification(context, bildirim)
            viewModel.consumeSystemNotificationEvent()
        }
    }

    when (val route = currentRoute) {
        is AppRoute.Detail -> {
            BackHandler { currentRoute = AppRoute.Home }
            val record = uiState.tumKayitlar.firstOrNull { it.id == route.recordId }
            if (record == null) {
                currentRoute = AppRoute.Home
            } else {
                DetailScreen(
                    kayit = record,
                    onBack = { currentRoute = AppRoute.Home }
                )
            }
        }

        is AppRoute.Home, is AppRoute.Admin -> {
            if (route is AppRoute.Admin) {
                BackHandler { currentRoute = AppRoute.Home }
            }
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = route is AppRoute.Home,
                            onClick = { currentRoute = AppRoute.Home },
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ListAlt,
                                    contentDescription = "İlanlar"
                                )
                            },
                            label = { Text("İlanlar") },
                            modifier = Modifier.testTag("nav_home_tab")
                        )
                        NavigationBarItem(
                            selected = route is AppRoute.Admin,
                            onClick = {
                                viewModel.clearYoneticiStatus()
                                currentRoute = AppRoute.Admin
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = "Yönetici Paneli"
                                )
                            },
                            label = { Text("Yönetici Paneli") },
                            modifier = Modifier.testTag("nav_admin_tab")
                        )
                    }
                }
            ) { innerPadding ->
                val contentModifier = Modifier.padding(innerPadding)
                if (route is AppRoute.Home) {
                    HomeScreen(
                        uiState = uiState,
                        onSearchChange = viewModel::setAramaMetni,
                        onFilterChange = viewModel::setSeciliFiltre,
                        onRefreshClick = { viewModel.refreshBelediye(force = true) },
                        onDismissBildirim = viewModel::dismissIncomingBildirim,
                        onRecordClick = { kayit ->
                            currentRoute = AppRoute.Detail(kayit.id)
                        },
                        onAdminClick = {
                            viewModel.clearYoneticiStatus()
                            currentRoute = AppRoute.Admin
                        },
                        modifier = contentModifier
                    )
                } else {
                    AdminScreen(
                        uiState = uiState,
                        currentUserEmail = currentUserEmail,
                        onSaveManuel = viewModel::saveManuelVefat,
                        onDeleteManuel = viewModel::deleteManuelVefat,
                        onSendBildirim = viewModel::sendCustomBildirim,
                        onDeleteBildirim = viewModel::deleteBildirim,
                        onSaveReklam = viewModel::saveReklam,
                        onClearStatus = viewModel::clearYoneticiStatus,
                        onPrepareSignOut = viewModel::markOfflineAndSignOut,
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}
