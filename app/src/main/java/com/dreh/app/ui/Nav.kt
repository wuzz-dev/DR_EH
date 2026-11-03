package com.dreh.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dreh.app.AppState

object Routes {
    const val START = "start"
    const val SPLASH = "splash"
    const val SIGN_UP = "signup"
    const val LOG_IN = "login"
    const val CHOOSE_PROFILE = "choose_profile"
    const val STUDENT_TYPE = "student_type"
    const val INTRO = "intro"
    const val REVIEWER = "reviewer"
    const val FOLDER = "folder/{folderId}"
    const val NOTE = "note/{folderId}/{fileId}"
    const val AI_REVIEWER = "ai_reviewer"
    const val CHAT = "chat"
    const val CALENDAR = "calendar"
}

@Composable
fun DrEhApp() {
    val nav = rememberNavController()
    // one process-wide state, restored from prefs so XP/vitals/tasks survive a cold open
    val app = remember { com.dreh.app.AppStateHolder.load(nav.context) }
    // per-open day tick: bars decay a little between days, streaks break without daily tasks
    LaunchedEffect(app) { app.onAppOpen() }
    val aiStore = remember {
        com.dreh.app.ai.AiStore(nav.context.applicationContext)
    }
    val gclassStore = remember {
        com.dreh.app.gclass.GClassStore(nav.context.applicationContext)
    }

    // warm the home and chat companion gifs while the start screen is on so they are ready
    // when each screen opens. Home fits both dimensions; chat keeps its width-based scale.
    // NOTE: no custom cache keys - GIFs must hit the cache as animated drawables, not bitmaps.
    LaunchedEffect(Unit) {
        val ctx = nav.context
        val dm = ctx.resources.displayMetrics
        // ~24dp status bar reserve, matching DesignFrame's safe-area fit (preload only)
        val sb = (24 * dm.density).toInt()
        val homeFit = minOf(dm.widthPixels / 412f, (dm.heightPixels - sb) / 917f)
        val chatFit = dm.widthPixels / 412f
        val loader = coil.Coil.imageLoader(ctx)
        fun preload(name: String, width: Float, fit: Float) {
            val res = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (res != 0) {
                val px = (width * fit).coerceIn(160f, 900f).toInt()
                loader.enqueue(
                    coil.request.ImageRequest.Builder(ctx)
                        .data(res)
                        .size(px)
                        .crossfade(false)
                        .build()
                )
            }
        }
        listOf(
            "pet_neutral" to 471f, "pet_happy" to 471f,
            "pet_tired" to 400f, "pet_sick" to 439f
        ).forEach { (name, width) -> preload(name, width, homeFit) }
        preload("listening", 270f, chatFit)
    }

    val toTab: (Tab) -> Unit = { tab ->
        nav.navigate(tab.route) { popUpTo(Tab.HOME.route) { inclusive = tab == Tab.HOME }; launchSingleTop = true }
    }
    val enterApp: () -> Unit = {
        nav.navigate(Tab.HOME.route) { popUpTo(Routes.START) { inclusive = true } }
    }

    NavHost(
        nav,
        startDestination = Routes.SPLASH,
        enterTransition = {
            fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 18 }
        },
        exitTransition = {
            fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 24 }
        },
        popEnterTransition = {
            fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it / 18 }
        },
        popExitTransition = {
            fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 24 }
        }
    ) {
        // themed loader splash on every cold open: straight to Home with a live session,
        // otherwise the onboarding flow (Figma order)
        composable(Routes.SPLASH) { SplashScreen(onDone = {
            if (app.session) {
                nav.navigate(Tab.HOME.route) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
            } else {
                nav.navigate(Routes.START) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
        }) }
        composable(Routes.START) { StartScreen({ nav.navigate(Routes.SIGN_UP) }, { nav.navigate(Routes.LOG_IN) }) }
        composable(Routes.SIGN_UP) {
            SignUpScreen(app, onCreated = { nav.navigate(Routes.CHOOSE_PROFILE) }, onLogIn = { nav.navigate(Routes.LOG_IN) })
        }
        composable(Routes.LOG_IN) {
            LogInScreen(app, onLoggedIn = enterApp, onSignUp = { nav.navigate(Routes.SIGN_UP) })
        }
        composable(Routes.CHOOSE_PROFILE) { ChooseProfileScreen(app) { nav.navigate(Routes.STUDENT_TYPE) } }
        composable(Routes.STUDENT_TYPE) { StudentTypeScreen(app) { nav.navigate(Routes.INTRO) } }
        composable(Routes.INTRO) { IntroScreen(onNext = enterApp) }

        // main tabs
        composable(Tab.HOME.route) {
            HomeScreen(app, toTab, onCalendar = { nav.navigate(Routes.CALENDAR) },
                onMakeReviewer = { nav.navigate(Routes.REVIEWER) },
                onAskAi = { nav.navigate(Routes.CHAT) })
        }
        composable(Tab.TASKS.route) { TasksScreen(app, gclassStore, toTab, onNotes = { nav.navigate(Routes.REVIEWER) }) }
        composable(Tab.REWARDS.route) { RewardsScreen(app, toTab) }
        composable(Tab.PROFILE.route) {
            ProfileScreen(app, toTab,
                onEdit = { nav.navigate(Routes.CHOOSE_PROFILE) },
                onLogOut = {
                    app.logOut(); app.resetProgress()
                    com.dreh.app.AppStateHolder.reset(nav.context)
                    nav.navigate(Routes.START) { popUpTo(0) }
                })
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(app, onBack = { nav.popBackStack() }, onTab = toTab,
                onAskAi = { nav.navigate(Routes.AI_REVIEWER) })
        }

        composable(Routes.AI_REVIEWER) {
            AiReviewerScreen(app, aiStore,
                onBack = { nav.popBackStack() },
                onSaveInto = { f, n, _ -> nav.navigate("note/$f/$n") },
                onTab = toTab)
        }

        // the companion chat ("Ask Dr. Eh")
        composable(Routes.CHAT) {
            ChatScreen(app, aiStore, onBack = { nav.popBackStack() })
        }

        // reviewer -> folder -> note
        composable(Routes.REVIEWER) {
            ReviewerScreen(app, null, onBack = { nav.popBackStack() },
                onOpenFolder = { nav.navigate("folder/$it") }, onOpenFile = { f, n -> nav.navigate("note/$f/$n") }, onTab = toTab)
        }
        composable(Routes.FOLDER, listOf(navArgument("folderId") { type = NavType.LongType })) { e ->
            val id = e.arguments!!.getLong("folderId")
            ReviewerScreen(app, id, onBack = { nav.popBackStack() },
                onOpenFolder = {}, onOpenFile = { f, n -> nav.navigate("note/$f/$n") }, onTab = toTab)
        }
        composable(Routes.NOTE, listOf(
            navArgument("folderId") { type = NavType.LongType },
            navArgument("fileId") { type = NavType.LongType })) { e ->
            NoteScreen(app, e.arguments!!.getLong("folderId"), e.arguments!!.getLong("fileId")) { nav.popBackStack() }
        }
    }
}
