package com.example.buddy

import com.example.buddy.ui.vocab.*

fun sourceTests() {
    val rows = listOf(
        SourceNode(1, null, "book", "Cambridge IELTS 18", null, 0),
        SourceNode(2, 1, "test", "Test 2", null, 0),
        SourceNode(3, 2, "part", "Reading 3", "reading", 3),
        SourceNode(4, 1, "test", "Test 1", null, 0),
        SourceNode(5, 99, "other", "Orphan", null, 1),
        SourceNode(6, 7, "other", "LoopA", null, 0),
        SourceNode(7, 6, "other", "LoopB", null, 0)
    )
    val tree = SourceTree.build(rows)
    val book = tree.firstOrNull { it.node.id == 1L }
    check("source tree nests", book != null && book.children.map { it.node.id } == listOf(2L, 4L) && book.children[0].children.single().node.id == 3L)
    check("source tree orphan is root", tree.any { it.node.id == 5L })
    check("source tree loop does not hang", tree.any { it.node.id == 6L || it.node.id == 7L })
    check("source path", SourceTree.path(rows, 3) == "Cambridge IELTS 18 › Test 2 › Reading 3")
    check("source path loop safe", SourceTree.path(rows, 6).isNotEmpty())

    check("topic keys unique", Topics.PRESETS.map { it.key }.toSet().size == Topics.PRESETS.size)
    check("topic names in three languages", Topics.PRESETS.all { it.ru.isNotBlank() && it.en.isNotBlank() && it.uz.isNotBlank() })
    check("topic display", Topics.display(TopicRow(1, "health", null, 0), "uz") == "Sog'liq" && Topics.display(TopicRow(2, null, "Animals", 0), "en") == "Animals")
    check("topic preset lookup", Topics.presetKeyFor("environment") == "environment" && Topics.presetKeyFor("Sog'liq") == "health" && Topics.presetKeyFor("Animals") == null)
}
