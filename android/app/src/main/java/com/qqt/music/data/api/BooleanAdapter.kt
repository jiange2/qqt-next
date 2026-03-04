package com.qqt.music.data.api

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/** Handles is_favourite field that comes as boolean, int or string from the API */
class BooleanAdapter : TypeAdapter<Boolean>() {
    override fun write(out: JsonWriter, value: Boolean?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Boolean {
        return when (reader.peek()) {
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER -> reader.nextInt() != 0
            JsonToken.STRING -> {
                val s = reader.nextString()
                s.toIntOrNull()?.let { it != 0 } ?: s.equals("true", ignoreCase = true)
            }
            JsonToken.NULL -> { reader.nextNull(); false }
            else -> { reader.skipValue(); false }
        }
    }
}
