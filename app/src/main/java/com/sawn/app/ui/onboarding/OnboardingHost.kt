package com.sawn.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.ui.SawnViewModel
import com.sawn.domain.OnboardingAnswers
import com.sawn.app.ui.onboarding.steps.WelcomeStep
import com.sawn.app.ui.onboarding.steps.ProfileStep
import com.sawn.app.ui.onboarding.steps.QuestionOneStep
import com.sawn.app.ui.onboarding.steps.QuestionTwoStep
import com.sawn.app.ui.onboarding.steps.QuestionThreeStep
import com.sawn.app.ui.onboarding.steps.QuestionFourStep
import com.sawn.app.ui.onboarding.steps.PlanRevealStep

private enum class OnboardStep { WELCOME, PROFILE, Q1, Q2, Q3, Q4, PLAN }

/**
 * الترحيب «فجر الأمل» — تدفق كامل: ترحيب ← ملف محلي ← 4 أسئلة ← خطتك الشخصية.
 * كل الإجابات محلية 100% (DataStore) — لا اتصال بالإنترنت.
 */
@Composable
fun OnboardingHost(vm: SawnViewModel, onDone: () -> Unit) {
    var step by remember { mutableStateOf(OnboardStep.WELCOME) }
    var answers by remember { mutableStateOf(OnboardingAnswers()) }
    val reduceMotion by vm.reduceMotion.collectAsStateWithLifecycle()

    BackHandler(enabled = step != OnboardStep.WELCOME) {
        step = when (step) {
            OnboardStep.PLAN -> OnboardStep.Q4
            OnboardStep.Q4 -> OnboardStep.Q3
            OnboardStep.Q3 -> OnboardStep.Q2
            OnboardStep.Q2 -> OnboardStep.Q1
            OnboardStep.Q1 -> OnboardStep.PROFILE
            OnboardStep.PROFILE -> OnboardStep.WELCOME
            OnboardStep.WELCOME -> OnboardStep.WELCOME
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "onboarding_step",
        ) { target ->
            when (target) {
                OnboardStep.WELCOME -> WelcomeStep(onStart = { step = OnboardStep.PROFILE })
                OnboardStep.PROFILE -> ProfileStep(
                    initialName = answers.name,
                    initialAge = answers.age,
                    onContinue = { name, age ->
                        answers = answers.copy(name = name, age = age)
                        step = OnboardStep.Q1
                    },
                    onSkip = { step = OnboardStep.Q1 },
                )
                OnboardStep.Q1 -> QuestionOneStep(
                    selected = answers.source.ifBlank { null },
                    onSelect = { answers = answers.copy(source = it); step = OnboardStep.Q2 },
                    onBack = { step = OnboardStep.PROFILE },
                )
                OnboardStep.Q2 -> QuestionTwoStep(
                    selected = answers.goal.ifBlank { null },
                    onSelect = { answers = answers.copy(goal = it); step = OnboardStep.Q3 },
                    onBack = { step = OnboardStep.Q1 },
                )
                OnboardStep.Q3 -> QuestionThreeStep(
                    selected = answers.commitment.ifBlank { null },
                    onSelect = { answers = answers.copy(commitment = it); step = OnboardStep.Q4 },
                    onBack = { step = OnboardStep.Q2 },
                )
                OnboardStep.Q4 -> QuestionFourStep(
                    selected = answers.content,
                    onChange = { answers = answers.copy(content = it) },
                    onBack = { step = OnboardStep.Q3 },
                    onBuildPlan = { step = OnboardStep.PLAN },
                )
                OnboardStep.PLAN -> PlanRevealStep(
                    answers = answers,
                    reduceMotion = reduceMotion,
                    onActivate = {
                        vm.completeOnboarding(answers)
                        onDone()
                    },
                    onExplore = {
                        vm.completeOnboarding(answers)
                        onDone()
                    },
                )
            }
        }
    }
}
