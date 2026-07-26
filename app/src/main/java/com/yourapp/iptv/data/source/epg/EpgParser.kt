package com.yourapp.iptv.data.source.epg

import com.yourapp.iptv.data.model.EpgXmltvDto
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

class EpgParser {
    fun parse(xmlContent: String): List<EpgXmltvDto> {
        val programs = mutableListOf<EpgXmltvDto>()
        
        try {
            val factory = SAXParserFactory.newInstance()
            val saxParser = factory.newSAXParser()
            
            val handler = object : DefaultHandler() {
                private var currentProgram: EpgXmltvDto? = null
                private var currentChannelId: String? = null
                private var currentTitle: String? = null
                private var currentDesc: String? = null
                private var buffer = StringBuilder()

                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    when (qName) {
                        "programme" -> {
                            currentProgram = EpgXmltvDto(
                                channelId = attributes?.getValue("channel") ?: "",
                                title = "",
                                start = attributes?.getValue("start") ?: "",
                                stop = attributes?.getValue("stop") ?: "",
                                desc = null
                            )
                        }
                        "title" -> buffer.clear()
                        "desc" -> buffer.clear()
                    }
                }

                override fun characters(ch: CharArray?, start: Int, length: Int) {
                    buffer.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    when (qName) {
                        "title" -> currentProgram = currentProgram?.copy(title = buffer.toString())
                        "desc" -> currentProgram = currentProgram?.copy(desc = buffer.toString())
                        "programme" -> {
                            currentProgram?.let { programs.add(it) }
                            currentProgram = null
                        }
                    }
                }
            }

            saxParser.parse(InputSource(StringReader(xmlContent)), handler)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return programs
    }
}