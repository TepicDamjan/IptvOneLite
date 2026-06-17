package com.example.iptvone.parser

import com.example.iptvone.model.Channel
import java.io.InputStream

object M3uParser {

    fun parse(inputStream: InputStream): List<Channel> {
        val channels = mutableListOf<Channel>()
        var currentName = ""

        inputStream.bufferedReader().use { reader ->
            reader.forEachLine { line ->
                val trimmedLine = line.trim()
                when {
                    trimmedLine.startsWith("#EXTINF:") -> {
                        currentName = trimmedLine.substringAfterLast(",").trim()
                    }
                    trimmedLine.startsWith("http://") || trimmedLine.startsWith("https://") -> {
                        if (currentName.isNotEmpty()) {
                            channels.add(Channel(name = currentName, url = trimmedLine.trim()))
                            currentName = ""
                        }
                    }
                }
            }
        }
        return channels
    }
}
