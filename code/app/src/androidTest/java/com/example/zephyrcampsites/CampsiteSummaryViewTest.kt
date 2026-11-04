package com.example.zephyrcampsites

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class CampsiteSummaryViewTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openFilter() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithText("Choose maximum distance for campsite: ").assertExists()
        composeTestRule.onNodeWithText("Click to select dates you want to stay...").assertExists()
        composeTestRule.onNodeWithText("Enter amount of guests (max: 10)").assertExists()
        composeTestRule.onNodeWithText("Reset filters").assertExists()
    }

    @Test
    fun verifyFilterPersistence_guestsLimitTest() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput("10")
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assertExists()
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
    }

    @Test
    fun guestsLimitTestFail() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput("11")
        composeTestRule.onNodeWithText("Max guests exceeded!").assertExists()
        composeTestRule.onNodeWithText("Dismiss").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
    }

    @Test
    fun verifyGuestsTextFieldFilter_mixLetter() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput("ajdk10ajkd")
        composeTestRule.onNodeWithTag("GuestsEntry").printToLog("TextFieldLog")
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assertExists()
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
    }

    @Test
    fun verifyGuestsTextFieldFilter_mixSpace() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextClearance()
        composeTestRule.onNodeWithTag("GuestsEntry").performTextInput(" 1   0 ")
        composeTestRule.onNodeWithTag("GuestsEntry").printToLog("TextFieldLog")
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("GuestsEntry").assertExists()
        composeTestRule.onNodeWithTag("GuestsEntry").assert(hasText("10"))
    }

    @Test
    fun testCalendarDoesNotBreak_StartEqualEnd() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("DateSelectButton").performClick()
        composeTestRule.onNodeWithText("Tuesday, November 3, 2026").performClick()
        composeTestRule.onNodeWithText("Tuesday, November 3, 2026").performClick()
        composeTestRule.onNodeWithTag("DateSelectConfirm").performClick()
        composeTestRule.onNodeWithText("Current selected date: ").assertExists()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
    }

    @Test
    fun testCalendarDoesNotBreak_StartBeforeEnd() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("DateSelectButton").performClick()
        composeTestRule.onNodeWithText("Tuesday, November 3, 2026").performClick()
        composeTestRule.onNodeWithText("Thursday, November 5, 2026").performClick()
        composeTestRule.onNodeWithTag("DateSelectConfirm").performClick()
        composeTestRule.onNodeWithText("Current selected date: ").assertExists()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
    }

    @Test
    fun testCalendarDoesNotBreak_EndBeforeStart() {
        composeTestRule.onNodeWithTag("FilterButton").performClick()
        composeTestRule.onNodeWithTag("DateSelectButton").performClick()
        composeTestRule.onNodeWithText("Thursday, November 5, 2026").performClick()
        composeTestRule.onNodeWithText("Tuesday, November 3, 2026").performClick()
        composeTestRule.onNodeWithTag("DateSelectConfirm").performClick()
        composeTestRule.onNodeWithText("Current selected date: ").assertExists()
        composeTestRule.onNodeWithTag("FilterButton").performClick()
    }

    @Test
    fun openDetails() {
        composeTestRule.onNodeWithTag("CampsiteDetails1").performClick()
        composeTestRule.onNodeWithText("test site 1").assertExists()
        Espresso.pressBack()
        composeTestRule.onNodeWithTag("CampsiteDetails2").performClick()
        composeTestRule.onNodeWithText("test site 2").assertExists()
    }
}