package dev.gestrin.hub.data

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Strict, flat versioned metadata format. Reject unknown fields, duplicates and nested payloads. */
internal object LibraryJson {
    private const val FORMAT = "gestrin-hub-library"

    suspend fun encode(games: List<Game>): ByteArray {
        require(games.size <= DataLimits.MAX_FILES) { "Export supports at most 5,000 games."
        }
        val buffer = ByteArrayOutputStream()
        val bounded = object : OutputStream() {
            override fun write(value: Int) {
                require(buffer.size() < DataLimits.MAX_LIBRARY_BYTES) { "Export exceeds 8 MiB."
                }
                buffer.write(value)
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                require(length <= DataLimits.MAX_LIBRARY_BYTES - buffer.size()) { "Export exceeds 8 MiB."
                }
                buffer.write(bytes, offset, length)
            }
        }
        JsonWriter(OutputStreamWriter(bounded, Charsets.UTF_8)).use { writer ->
            writer.beginObject()
            writer.name("format").value(FORMAT)
            writer.name("schemaVersion").value(1L)
            writer.name("games").beginArray()
            for (raw in games) {
                currentCoroutineContext().ensureActive()
                val game = validateGame(raw)
                writer.beginObject()
                writer.name("id").value(game.id)
                writer.name("title").value(game.title)
                writer.name("platform").value(game.platform)
                writer.name("uri").value(game.uri)
                writer.name("isDirectory").value(game.isDirectory)
                writer.name("favorite").value(game.favorite)
                writer.name("coverUri").value(game.coverUri)
                writer.name("lastOpened").value(game.lastOpened)
                writer.endObject()
            }
            writer.endArray().endObject()
        }
        return buffer.toByteArray()
    }

    suspend fun decode(bytes: ByteArray): List<Game> {
        require(bytes.size <= DataLimits.MAX_LIBRARY_BYTES) { "Library exceeds 8 MiB."
        }
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        val games = mutableListOf<Game>()
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            expect(reader, JsonToken.BEGIN_OBJECT)
            reader.beginObject()
            val seen = hashSetOf<String>()
            var format: String? = null
            var version: Long? = null
            while (reader.hasNext()) {
                currentCoroutineContext().ensureActive()
                val key = reader.nextName()
                require(seen.add(key)) { "Duplicate root field."
                }
                when (key) {
                    "format" -> format = string(reader, 64)
                    "schemaVersion" -> version = integer(reader)
                    "games" -> {
                        expect(reader, JsonToken.BEGIN_ARRAY)
                        reader.beginArray()
                        while (reader.hasNext()) {
                            currentCoroutineContext().ensureActive()
                            require(games.size < DataLimits.MAX_FILES) { "Library exceeds 5,000 games."
                            }
                            games.add(readGame(reader))
                        }
                        reader.endArray()
                    }
                    else -> throw IllegalArgumentException("Unknown library field.")
                }
            }
            reader.endObject()
            expect(reader, JsonToken.END_DOCUMENT)
            require(format == FORMAT && version == 1L && "games" in seen) {
                "Expected gestrin-hub-library schemaVersion 1 and a games array."
            }
        }
        return games
    }

    private fun readGame(reader: JsonReader): Game {
        expect(reader, JsonToken.BEGIN_OBJECT)
        reader.beginObject()
        val seen = hashSetOf<String>()
        var id: String? = null
        var title: String? = null
        var platform: String? = null
        var uri: String? = null
        var directory: Boolean? = null
        var favorite = false
        var coverUri: String? = null
        var lastOpened = 0L
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(seen.add(key)) { "Duplicate game field."
            }
            when (key) {
                "id" -> id = string(reader, 36)
                "title" -> title = string(reader, DataLimits.MAX_TITLE)
                "platform" -> platform = string(reader, 16)
                "uri" -> uri = string(reader, DataLimits.MAX_URI)
                "isDirectory" -> directory = boolean(reader)
                "favorite" -> favorite = boolean(reader)
                "coverUri" -> {
                    if (reader.peek() == JsonToken.NULL) reader.nextNull()
                    else coverUri = string(reader, DataLimits.MAX_URI)
                }
                "lastOpened" -> lastOpened = integer(reader)
                else -> throw IllegalArgumentException("Unknown game field.")
            }
        }
        reader.endObject()
        return validateGame(Game(
            id = requireNotNull(id) { "Missing game id." },
            title = requireNotNull(title) { "Missing game title." },
            platform = requireNotNull(platform) { "Missing game platform." },
            uri = requireNotNull(uri) { "Missing game URI." },
            isDirectory = requireNotNull(directory) { "Missing isDirectory." },
            favorite = favorite, coverUri = coverUri, lastOpened = lastOpened
        ))
    }

    private fun expect(reader: JsonReader, token: JsonToken) {
        require(reader.peek() == token) { "Unexpected JSON value type."
        }
    }

    private fun string(reader: JsonReader, max: Int): String {
        expect(reader, JsonToken.STRING)
        return reader.nextString().also { require(it.length <= max) { "JSON string exceeds field limit." } }
    }

    private fun boolean(reader: JsonReader): Boolean {
        expect(reader, JsonToken.BOOLEAN)
        return reader.nextBoolean()
    }

    private fun integer(reader: JsonReader): Long {
        expect(reader, JsonToken.NUMBER)
        val value = reader.nextString()
        require(value.length <= 19 && value.matches(Regex("0|[1-9][0-9]*"))) {
            "Expected a nonnegative integer timestamp/version."
        }
        return requireNotNull(value.toLongOrNull()) { "Integer exceeds Long range." }
    }
}
