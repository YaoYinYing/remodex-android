package com.remodex.mobile.service.transport

import com.remodex.mobile.model.TimelineRole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcTransportParserTest {
    private val parser = RpcTransportParser()

    @Test
    fun parseThreadListSupportsDataItemsAndThreadsShapes() {
        val dataResult = JsonObject(
            mapOf(
                "data" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("thread-data"),
                                "title" to JsonPrimitive("Data shape")
                            )
                        )
                    )
                )
            )
        )
        val itemsResult = JsonObject(
            mapOf(
                "items" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("thread-items"),
                                "name" to JsonPrimitive("Items shape")
                            )
                        )
                    )
                )
            )
        )
        val threadsResult = JsonObject(
            mapOf(
                "threads" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("thread-threads"),
                                "preview" to JsonPrimitive("Threads shape")
                            )
                        )
                    )
                )
            )
        )

        assertEquals("thread-data", parser.parseThreadList(dataResult).single().id)
        assertEquals("thread-items", parser.parseThreadList(itemsResult).single().id)
        assertEquals("thread-threads", parser.parseThreadList(threadsResult).single().id)
    }

    @Test
    fun parseThreadReadTimelineNormalizesRolesAndText() {
        val result = JsonObject(
            mapOf(
                "thread" to JsonObject(
                    mapOf(
                        "id" to JsonPrimitive("thread-1"),
                        "turns" to JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive("turn-1"),
                                        "items" to JsonArray(
                                            listOf(
                                                JsonObject(
                                                    mapOf(
                                                        "id" to JsonPrimitive("item-user"),
                                                        "type" to JsonPrimitive("user_message"),
                                                        "content" to JsonArray(
                                                            listOf(
                                                                JsonObject(
                                                                    mapOf(
                                                                        "type" to JsonPrimitive("text"),
                                                                        "text" to JsonPrimitive("hello")
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                ),
                                                JsonObject(
                                                    mapOf(
                                                        "id" to JsonPrimitive("item-assistant"),
                                                        "type" to JsonPrimitive("assistant_message"),
                                                        "content" to JsonArray(
                                                            listOf(
                                                                JsonObject(
                                                                    mapOf(
                                                                        "type" to JsonPrimitive("output_text"),
                                                                        "text" to JsonPrimitive("world")
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                ),
                                                JsonObject(
                                                    mapOf(
                                                        "id" to JsonPrimitive("item-system"),
                                                        "type" to JsonPrimitive("reasoning"),
                                                        "text" to JsonPrimitive("thinking")
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val timeline = parser.parseThreadTimeline(result)
        assertEquals(3, timeline.size)
        assertEquals(TimelineRole.USER, timeline[0].role)
        assertEquals("hello", timeline[0].text)
        assertEquals(TimelineRole.ASSISTANT, timeline[1].role)
        assertEquals("world", timeline[1].text)
        assertEquals(TimelineRole.SYSTEM, timeline[2].role)
        assertTrue(timeline[2].text.contains("thinking"))
    }

    @Test
    fun parseThreadListExtractsParentForkAndAgentMetadata() {
        val result = JsonObject(
            mapOf(
                "threads" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("thread-parent"),
                                "title" to JsonPrimitive("Parent"),
                                "model" to JsonPrimitive("gpt-5.4")
                            )
                        ),
                        JsonObject(
                            mapOf(
                                "id" to JsonPrimitive("thread-child"),
                                "name" to JsonPrimitive("Child"),
                                "parent_thread_id" to JsonPrimitive("thread-parent"),
                                "metadata" to JsonObject(
                                    mapOf(
                                        "forked_from_thread_id" to JsonPrimitive("thread-origin"),
                                        "agent_nickname" to JsonPrimitive("Scout"),
                                        "agent_role" to JsonPrimitive("explorer"),
                                        "model_provider" to JsonPrimitive("openai")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val parsed = parser.parseThreadList(result)
        assertEquals(2, parsed.size)

        val child = parsed.first { it.id == "thread-child" }
        assertEquals("thread-parent", child.parentThreadId)
        assertEquals("thread-origin", child.forkedFromThreadId)
        assertEquals("Scout", child.agentNickname)
        assertEquals("explorer", child.agentRole)
        assertEquals("openai", child.modelProvider)
        assertTrue(child.isSubagent)
        assertTrue(child.isForkedThread)
    }
}
