package com.twomemory.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class CloudConnectionActivityTest {
    @get:Rule val compose = createAndroidComposeRule<CloudConnectionActivity>()
    @Test fun cloudBindingIsReachableWithoutErasingTheExistingSpace() {
        compose.onNodeWithText("连接云端空间").assertIsDisplayed()
        compose.onNodeWithText("连接设置").performScrollTo().performClick()
        compose.onNodeWithText("服务器地址").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("创建手账").assertIsDisplayed()
        compose.onNodeWithText("接受邀请").assertIsDisplayed()
    }
}
