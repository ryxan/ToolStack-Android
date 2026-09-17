package com.toolstack.io

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.ui.bearings.BearingsScreen
import com.toolstack.io.ui.conduitbends.ConduitBendsScreen
import com.toolstack.io.ui.home.HomeScreen
import com.toolstack.io.ui.saemetric.SaeMetricScreen
import com.toolstack.io.ui.tapsanddrills.TapsAndDrillsScreen
import com.toolstack.io.ui.ratiomix.RatioMixScreen
import com.toolstack.io.ui.shortcuts.ShortcutPaywallHost
import com.toolstack.io.ui.shortcuts.ShortcutViewModel
import com.toolstack.io.ui.shortcuts.shortcutIconForRoute
import com.toolstack.io.ui.shortcuts.shortcutLabelResForRoute
import com.toolstack.io.ui.unitconverter.UnitConverterDetailScreen
import com.toolstack.io.ui.unitconverter.UnitConverterScreen
import com.toolstack.io.ui.unitconverter.UnitConverterViewModel
import com.toolstack.io.ui.wrenchfastener.WrenchFastenerScreen
import com.toolstack.io.ui.theme.IndustrialUtilityTheme
import com.toolstack.io.util.ShortcutUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    private var isAppReady = false

    /**
     * Emits shortcut route strings arriving via [onNewIntent] while the
     * activity is already running. Capacity of 1 ensures a rapid double-tap
     * does not queue two navigations; the collector in [setContent] processes
     * one emission and any duplicate is dropped.
     */
    private val _shortcutRoute = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val shortcutRoute: SharedFlow<String> = _shortcutRoute.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract a deep-link route from the launch intent (e.g. from a home
        // screen shortcut: toolstack://screen/sae_metric).
        // Read unconditionally so that cold launches always pick up the route.
        // Double-navigation on configuration change is prevented by pendingDeepLink
        // being consumed (set to null) after the first navigate call below.
        val deepLinkRoute: String? = ShortcutUtil.extractRoute(intent?.data)

        // If launching directly to a shortcut, hold the splash screen until initial
        // navigation completes so the user doesn't see an intermediate screen flash.
        isAppReady = deepLinkRoute == null
        splashScreen.setKeepOnScreenCondition { !isAppReady }

        setContent {
            IndustrialUtilityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = LocalContext.current

                    // Single ShortcutViewModel instance for all tool screens.
                    // hiltViewModel() here is scoped to the activity's
                    // ViewModelStoreOwner, so every composable that calls it
                    // with the same owner receives the same instance.
                    val shortcutViewModel: ShortcutViewModel = hiltViewModel()
                    val shortcutUiState by shortcutViewModel.uiState.collectAsState()

                    // Builds the onAddShortcut lambda for a given route.
                    // Passing null suppresses the button entirely; the lambda
                    // is always non-null here so the icon always shows — gating
                    // (paywall vs. immediate shortcut) is inside the ViewModel.
                    fun addShortcutFor(route: String): () -> Unit = {
                        shortcutViewModel.onAddShortcutClicked(
                            context = context,
                            route = route,
                            label = context.getString(shortcutLabelResForRoute(route)),
                            iconResId = shortcutIconForRoute(route)
                        )
                    }

                    // Render paywall / purchase-error dialogs on top of whatever
                    // screen is currently visible. ShortcutPaywallHost is
                    // stateless — it only shows when showPaywall/purchaseError
                    // are set on the shared ViewModel.
                    ShortcutPaywallHost(
                        uiState = shortcutUiState,
                        shortcutViewModel = shortcutViewModel
                    )

                    // Track whether we've already consumed the deep-link route so
                    // we don't navigate again on recomposition.
                    val pendingDeepLink = remember { mutableStateOf(deepLinkRoute) }

                    // Dismiss the splash screen if the app is restored directly onto a
                    // non-Home destination (e.g. activity recreated after a shortcut
                    // deep-link). Without this, isAppReady stays false and the splash
                    // screen is held forever because the Home composable never runs.
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    LaunchedEffect(currentBackStackEntry?.destination?.route) {
                        val route = currentBackStackEntry?.destination?.route
                        if (!route.isNullOrEmpty() && route != Screen.Home.route) {
                            isAppReady = true
                        }
                    }

                    // Warm-launch handler: consume shortcut routes emitted by onNewIntent
                    // and navigate without restarting the Activity. popUpTo(Home) resets
                    // the back stack to Home → Tool, matching the cold-launch shape and
                    // preventing a stale destination from sitting below the new screen.
                    LaunchedEffect(Unit) {
                        shortcutRoute.collect { route ->
                            if (navController.graph.findNode(route) != null) {
                                navController.navigate(route) {
                                    popUpTo(Screen.Home.route) { saveState = false }
                                    launchSingleTop = true
                                }
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            // After NavHost is composed, consume a pending deep-link
                            // by navigating away from Home.  We do it here rather
                            // than before NavHost so the graph is already built.
                            pendingDeepLink.value?.let { route ->
                                pendingDeepLink.value = null
                                if (navController.graph.findNode(route) != null) {
                                    navController.navigate(route)
                                }
                                isAppReady = true
                            }

                            HomeScreen(
                                onNavigate = { route ->
                                    if (navController.graph.findNode(route) != null) {
                                        navController.navigate(route)
                                    }
                                }
                            )
                        }
                        composable(Screen.SaeMetric.route) {
                            SaeMetricScreen(
                                onBack = { navController.popBackStack() },
                                onAddShortcut = addShortcutFor(Screen.SaeMetric.route)
                            )
                        }
                        composable(Screen.WrenchFastener.route) {
                            WrenchFastenerScreen(
                                onBack = { navController.popBackStack() },
                                onAddShortcut = addShortcutFor(Screen.WrenchFastener.route)
                            )
                        }
                        composable(Screen.TapsAndDrills.route) {
                            TapsAndDrillsScreen(
                                onBack = { navController.popBackStack() },
                                onAddShortcut = addShortcutFor(Screen.TapsAndDrills.route)
                            )
                        }
                        composable(Screen.Bearings.route) {
                            BearingsScreen(
                                onBack = { navController.popBackStack() },
                                onAddShortcut = addShortcutFor(Screen.Bearings.route)
                            )
                        }
                        composable(Screen.ConduitBends.route) {
                            ConduitBendsScreen(
                                onBack = { navController.popBackStack() },
                                onAddShortcut = addShortcutFor(Screen.ConduitBends.route)
                            )
                        }
                        composable(Screen.UnitConverter.route) { navBackStackEntry ->
                            UnitConverterScreen(
                                onBack = { navController.popBackStack() },
                                onCategorySelected = { categoryIndex ->
                                    // Guard against double-taps and taps during the back
                                    // transition — only navigate when this entry is RESUMED.
                                    if (navBackStackEntry.lifecycle.currentState
                                        == androidx.lifecycle.Lifecycle.State.RESUMED
                                    ) {
                                        navController.navigate(
                                            Screen.UnitConverterDetail.routeWithArg(categoryIndex)
                                        )
                                    }
                                },
                                onAddShortcut = addShortcutFor(Screen.UnitConverter.route)
                            )
                        }
                        composable(Screen.UnitConverterDetail.route) { backStackEntry ->
                            val categoryIndex = backStackEntry.arguments
                                ?.getString(UnitConverterViewModel.ARG_CATEGORY_INDEX)
                                ?.toIntOrNull() ?: 0
                            UnitConverterDetailScreen(
                                categoryIndex = categoryIndex,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Screen.RatioMix.route) {
                            RatioMixScreen(
                                onBack = { navController.popBackStack() },
                                onAddShortcut = addShortcutFor(Screen.RatioMix.route)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when a shortcut intent arrives while the activity is already
     * running (singleTop / CLEAR_TOP re-delivery). Emits the route into
     * [shortcutRoute] so the NavController collector inside [setContent] can
     * navigate directly — no Activity restart needed.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShortcutUtil.extractRoute(intent.data)?.let { route ->
            _shortcutRoute.tryEmit(route)
        }
    }
}

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object SaeMetric : Screen("sae_metric")
    data object WrenchFastener : Screen("wrench_fastener")
    data object TapsAndDrills : Screen("taps_and_drills")
    data object Bearings : Screen("bearings")
    data object ConduitBends : Screen("conduit_bends")
    data object UnitConverter : Screen("unit_converter")
    data object UnitConverterDetail : Screen("unit_converter_detail/{${UnitConverterViewModel.ARG_CATEGORY_INDEX}}") {
        fun routeWithArg(categoryIndex: Int) = "unit_converter_detail/$categoryIndex"
    }
    data object RatioMix : Screen("ratio_mix")
}
