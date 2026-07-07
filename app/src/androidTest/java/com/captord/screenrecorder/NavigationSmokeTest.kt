package com.captord.screenrecorder

import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Settings", substring = false).assertExists()

        composeRule.onNodeWithText("Gallery").performClick()
        // Empty gallery copy or title
        composeRule.onNodeWithText("Gallery").assertExists()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Captord").assertExists()
    }
}
