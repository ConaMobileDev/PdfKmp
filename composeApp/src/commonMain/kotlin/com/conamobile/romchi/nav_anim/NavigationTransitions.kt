package com.conamobile.romchi.nav_anim

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

object NavigationTransitions {
    // Animation duration constants
    private const val ANIMATION_DURATION = 350
    private const val FADE_DURATION = 300

    // Parallax effect offset (iOS-style)
    private const val PARALLAX_FACTOR = 0.3f

    // Scale factors for depth effect
    private const val SCALE_IN = 0.95f
    private const val SCALE_OUT = 1.05f

    // Fade alpha values
    private const val FADE_ALPHA_MIN = 0.5f

    // Enter transition - o'ngdan chapga kirish
    val enterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = FADE_DURATION,
                easing = FastOutSlowInEasing
            ),
            initialAlpha = FADE_ALPHA_MIN
        ) + scaleIn(
            initialScale = SCALE_IN,
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Exit transition - current screen chapga siljiydi (parallax effect bilan)
    val exitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> (-fullWidth * PARALLAX_FACTOR).toInt() },
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = FADE_DURATION,
                delayMillis = 50,
                easing = FastOutSlowInEasing
            ),
            targetAlpha = FADE_ALPHA_MIN
        ) + scaleOut(
            targetScale = SCALE_OUT,
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Pop enter transition - orqaga qaytishda old screen o'ngdan kiradi
    val popEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { fullWidth -> (-fullWidth * PARALLAX_FACTOR).toInt() },
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = FADE_DURATION,
                easing = FastOutSlowInEasing
            ),
            initialAlpha = FADE_ALPHA_MIN
        ) + scaleIn(
            initialScale = SCALE_OUT,
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Pop exit transition - current screen o'ngga chiqadi
    val popExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = FADE_DURATION,
                delayMillis = 50,
                easing = FastOutSlowInEasing
            ),
            targetAlpha = FADE_ALPHA_MIN
        ) + scaleOut(
            targetScale = SCALE_IN,
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutSlowInEasing
            )
        )
    }
}

// Alternative: Material Design 3 inspired transitions with iOS feel
object MaterialIOSTransitions {
    private const val DURATION = 400
    private const val STAGGER_DELAY = 50

    val enterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(
                durationMillis = DURATION,
                easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DURATION / 2,
                delayMillis = STAGGER_DELAY,
                easing = LinearEasing
            )
        )
    }

    val exitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -it / 3 },
            animationSpec = tween(
                durationMillis = DURATION,
                easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = DURATION / 2,
                easing = LinearEasing
            )
        )
    }

    val popEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -it / 3 },
            animationSpec = tween(
                durationMillis = DURATION,
                easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = DURATION / 2,
                delayMillis = STAGGER_DELAY,
                easing = LinearEasing
            )
        )
    }

    val popExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(
                durationMillis = DURATION,
                easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
            )
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = DURATION / 2,
                easing = LinearEasing
            )
        )
    }
}


// Modal style transitions (bottom to top)
object ModalTransitions {
    val enterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { 0 },
            animationSpec = tween(400)
        ) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
    }

    val exitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(300))
    }

    val popEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(300))
    }

    val popExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
    }
}

// Fade transitions
object FadeTransitions {
    val enterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(300))
    }

    val exitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(300))
    }

    val popEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        fadeIn(animationSpec = tween(300))
    }

    val popExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        fadeOut(animationSpec = tween(300))
    }
}