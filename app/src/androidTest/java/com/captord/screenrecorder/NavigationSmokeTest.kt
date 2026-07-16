package com.captord.screenrecorder

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNav_reachesSettingsAndGallery() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Engine").assertExists()

        composeRule.onNodeWithContentDescription("Gallery").performClick()
        composeRule.onNode(hasText("Gallery") and !hasClickAction()).assertExists()

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.onNodeWithText("Captord").assertExists()
    }
}
