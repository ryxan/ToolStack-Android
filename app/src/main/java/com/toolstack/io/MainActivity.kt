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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toolstack.io.data.repository.UserPreferencesRepository
import com.toolstack.io.domain.calculator.UnitConverterData
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    private var isAppReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract a deep-link route from the launch intent (e.g. from a home
        // screen shortcut: toolstack://screen/sae_metric).
        // Read unconditionally so that warm relaunches via onNewIntent+recreate()
        // also navigate — savedInstanceState is non-null during recreate, so
        // guarding on it would silently drop shortcut routes after the first launch.
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
     * running (singleTop / CLEAR_TOP re-delivery).  The NavController is not
     * directly accessible here so we delegate by restarting via recreate() —
     * savedInstanceState will be null on the resulting onCreate, which means
     * the deep-link extraction path will run again.
     *
     * A more elegant solution would expose the NavController via a
     * SharedFlow, but this keeps the plumbing minimal while the feature is new.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Only recreate if this is actually a shortcut deep-link, not a
        // generic re-launch (e.g. tapping the launcher icon normally).
        if (ShortcutUtil.extractRoute(intent.data) != null) {
            recreate()
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
