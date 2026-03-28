package com.remodex.mobile.ui.parity

import com.remodex.mobile.model.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowParityHelpersTest {
    @Test
    fun detectComposerAutocompleteTokenRecognizesFileSkillAndCommand() {
        val file = detectComposerAutocompleteToken("check @CodexService")
        val skill = detectComposerAutocompleteToken("run \$openai-docs")
        val command = detectComposerAutocompleteToken("/status")

        assertTrue(file is ComposerAutocompleteToken.File)
        assertTrue(skill is ComposerAutocompleteToken.Skill)
        assertTrue(command is ComposerAutocompleteToken.Command)
    }

    @Test
    fun applyComposerAutocompleteSelectionReplacesTrailingToken() {
        val token = detectComposerAutocompleteToken("please check @Cod")
            ?: error("Expected token")
        val output = applyComposerAutocompleteSelection(
            originalInput = "please check @Cod",
            token = token,
            replacement = "@CodexService.kt"
        )

        assertEquals("please check @CodexService.kt ", output)
    }

    @Test
    fun groupThreadsByProjectAggregatesByCwd() {
        val threads = listOf(
            ThreadSummary(
                id = "a",
                title = "A",
                name = null,
                preview = null,
                cwd = "/repo/one",
                updatedAtMillis = 20
            ),
            ThreadSummary(
                id = "b",
                title = "B",
                name = null,
                preview = null,
                cwd = "/repo/one/",
                updatedAtMillis = 30
            ),
            ThreadSummary(
                id = "c",
                title = "C",
                name = null,
                preview = null,
                cwd = "/repo/two",
                updatedAtMillis = 10
            )
        )

        val groups = groupThreadsByProject(threads, query = "")
        assertEquals(2, groups.size)
        assertEquals("/repo/one", groups.first().projectPath)
        assertEquals(2, groups.first().threads.size)
    }
}
