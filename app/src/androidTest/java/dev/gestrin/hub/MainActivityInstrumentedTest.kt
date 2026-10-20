package dev.gestrin.hub

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Before
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** ActivityScenario launches the production activity; no replacement Composable or fake actions. */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetLanguage() {
        compose.runOnUiThread { androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java].selectLanguage("zh") }
        compose.onNode(tab("游戏库")).performClick()
    }

    @Test
    fun mainActivityRendersTabsAndNavigatesToRealPages() {
        compose.onNodeWithText("Gestrin Hub").assertIsDisplayed()
        listOf("游戏库", "运行器", "设置").forEach { label ->
            compose.onNode(tab(label)).assertIsDisplayed()
        }
        compose.onNode(tab("游戏库")).assertIsSelected()
        compose.onNodeWithText("暂无游戏").assertIsDisplayed()
        compose.onNodeWithText("你的游戏，尽在一处。").assertDoesNotExist()

        compose.onNode(tab("运行器")).performClick().assertIsSelected()
        compose.onNode(tab("游戏库")).assertIsNotSelected()
        compose.onNodeWithText("Winlator").assertIsDisplayed()

        compose.onNode(tab("设置")).performClick().assertIsSelected()
        compose.onNode(tab("运行器")).assertIsNotSelected()
        compose.onNodeWithText("语言").assertIsDisplayed()

        compose.onNode(tab("游戏库")).performClick().assertIsSelected()
        compose.onNodeWithText("暂无游戏").assertIsDisplayed()
    }

    @Test
    fun selectedTabSurvivesActivityRecreation() {
        compose.onNode(tab("设置")).performClick().assertIsSelected()
        compose.onNodeWithText("语言").assertIsDisplayed()

        compose.activityRule.scenario.recreate()

        compose.onNode(tab("设置")).assertIsDisplayed().assertIsSelected()
        compose.onNode(tab("游戏库")).assertIsNotSelected()
        compose.onNodeWithText("语言").assertIsDisplayed()
    }

    @Test fun languageSwitchUpdatesPagesDialogsAndSurvivesRecreation() {
        try {
            compose.onNode(tab("设置")).performClick()
            compose.onNodeWithText("English").performClick()
            compose.onNode(tab("Settings")).assertIsSelected()
            compose.onNodeWithText("Language").assertIsDisplayed()
            compose.onNodeWithText("Dark theme").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNode(tab("Settings")).assertIsSelected()
            compose.onNodeWithText("Language").assertIsDisplayed()
            compose.onNode(tab("Library")).performClick()
            compose.onNodeWithText("No games").assertIsDisplayed()
            compose.onNodeWithText("Add game").performClick()
            compose.onNodeWithText("Add source").assertIsDisplayed()
            compose.onNodeWithText("Choose folder").assertIsDisplayed()
            compose.onNodeWithText("Choose file").assertIsDisplayed()
        } finally {
            compose.activity.getSharedPreferences("hub", 0).edit().putString("language", "zh").commit()
        }
    }

    @Test fun windowsGameOpensOptionsInsteadOfAutomaticallyLeavingTheHub() {
        compose.runOnUiThread {
            val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java]
            vm.state.value = vm.state.value.copy(games = listOf(dev.gestrin.hub.ui.GameItem(
                "launch-regression", "Launch regression", "WINDOWS", "content://fixture/game.exe", false, false, null, 0
            )))
        }
        compose.onNodeWithText("Launch regression").performClick()
        compose.onNodeWithText("启动方式").performScrollTo().performClick()
        compose.onNodeWithText("此项目需在运行器内选择游戏；不会自动跳转。").assertIsDisplayed()
        compose.onNodeWithText("选择本机应用").assertIsDisplayed()
        compose.onNodeWithText("交给已选运行器").assertDoesNotExist()
    }

    // Clickable merged navigation nodes work for both NavigationBarItem and NavigationRailItem.
    private fun tab(label: String): SemanticsMatcher = hasText(label) and hasClickAction()
}
