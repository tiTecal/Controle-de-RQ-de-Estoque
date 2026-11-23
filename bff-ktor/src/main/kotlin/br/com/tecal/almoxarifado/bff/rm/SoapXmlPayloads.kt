package br.com.tecal.almoxarifado.bff.rm

import br.com.tecal.almoxarifado.bff.domain.MovementAlreadyWithdrawnException
import br.com.tecal.almoxarifado.bff.domain.MovementNotFoundException
import br.com.tecal.almoxarifado.bff.domain.TotvsRmIntegrationException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object SoapXmlPayloads {

    fun buildAutenticaAcessoEnvelope(): String = """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tot="http://www.totvs.com/">
           <soapenv:Header/>
           <soapenv:Body>
              <tot:AutenticaAcesso/>
           </soapenv:Body>
        </soapenv:Envelope>
    """.trimIndent()

    fun buildRealizarConsultaSqlEnvelope(
        codSentenca: String,
        codColigada: Int,
        codSistema: String,
        parameters: String
    ): String = """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tot="http://www.totvs.com/">
           <soapenv:Header/>
           <soapenv:Body>
              <tot:RealizarConsultaSQL>
                 <tot:codSentenca>${escapeXml(codSentenca)}</tot:codSentenca>
                 <tot:codColigada>$codColigada</tot:codColigada>
                 <tot:codSistema>${escapeXml(codSistema)}</tot:codSistema>
                 <tot:parameters>${escapeXml(parameters)}</tot:parameters>
              </tot:RealizarConsultaSQL>
           </soapenv:Body>
        </soapenv:Envelope>
    """.trimIndent()

    fun buildReadRecordEnvelope(
        dataServerName: String,
        primaryKey: String,
        contexto: String
    ): String = """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tot="http://www.totvs.com/">
           <soapenv:Header/>
           <soapenv:Body>
              <tot:ReadRecord>
                 <tot:DataServerName>${escapeXml(dataServerName)}</tot:DataServerName>
                 <tot:PrimaryKey>${escapeXml(primaryKey)}</tot:PrimaryKey>
                 <tot:Contexto>${escapeXml(contexto)}</tot:Contexto>
              </tot:ReadRecord>
           </soapenv:Body>
        </soapenv:Envelope>
    """.trimIndent()

    fun buildSaveRecordEnvelope(
        dataServerName: String,
        recordXml: String,
        contexto: String
    ): String = """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tot="http://www.totvs.com/">
           <soapenv:Header/>
           <soapenv:Body>
              <tot:SaveRecord>
                 <tot:DataServerName>${escapeXml(dataServerName)}</tot:DataServerName>
                 <tot:XML><![CDATA[$recordXml]]></tot:XML>
                 <tot:Contexto>${escapeXml(contexto)}</tot:Contexto>
              </tot:SaveRecord>
           </soapenv:Body>
        </soapenv:Envelope>
    """.trimIndent()

    /**
     * Extrai as linhas <Resultado> retornadas pelo método RealizarConsultaSQL do TOTVS RM.
     * As chaves do mapa são normalizadas para UPPERCASE para leitura consistente.
     */
    fun parseConsultaSqlRows(soapResponseXml: String): List<Map<String, String>> {
        val outerDoc = parseXmlSafe(soapResponseXml)
        checkSoapFault(outerDoc)

        val resultNode = findFirstElementByLocalName(outerDoc.documentElement, "RealizarConsultaSQLResult")
            ?: return emptyList()
        val innerXml = resultNode.textContent?.trim().orEmpty()
        if (innerXml.isEmpty() || innerXml == "<NewDataSet />" || innerXml == "<NewDataSet/>") {
            return emptyList()
        }

        val innerDoc = parseXmlSafe(innerXml)
        val resultadoNodes = innerDoc.documentElement.childNodes
        val rows = mutableListOf<Map<String, String>>()

        for (i in 0 until resultadoNodes.length) {
            val node = resultadoNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName.equals("Resultado", ignoreCase = true)) {
                val rowMap = mutableMapOf<String, String>()
                val fields = node.childNodes
                for (j in 0 until fields.length) {
                    val field = fields.item(j)
                    if (field.nodeType == Node.ELEMENT_NODE) {
                        rowMap[field.nodeName.uppercase()] = field.textContent?.trim().orEmpty()
                    }
                }
                rows.add(rowMap)
            }
        }
        return rows
    }

    /**
     * Extrai o XML interno de <ReadRecordResult> do MovMovimentoTBCData.
     */
    fun extractReadRecordXml(soapResponseXml: String, idMov: Int): String {
        val outerDoc = parseXmlSafe(soapResponseXml)
        checkSoapFault(outerDoc)

        val resultNode = findFirstElementByLocalName(outerDoc.documentElement, "ReadRecordResult")
            ?: throw TotvsRmIntegrationException("Resposta inválida do RM: tag ReadRecordResult ausente.")

        val innerXml = resultNode.textContent?.trim().orEmpty()
        if (innerXml.isEmpty() || !innerXml.contains("TMOV", ignoreCase = true)) {
            throw MovementNotFoundException(idMov)
        }
        return innerXml
    }

    /**
     * Atualiza cirurgicamente apenas os 3 campos complementares (RETIRADO_POR, DATA_RETIRADA, HORA_RETIRADA)
     * dentro de <TMOVCOMPL> no XML do MovMovimentoTBCData, preservando <TMOV> e <TITMMOV> intactos.
     * Retorna Pair(xmlAtualizado, numeroMov).
     */
    fun injectTmovComplWithdrawalFields(
        movementXml: String,
        codColigada: Int,
        idMov: Int,
        colRetiradoPor: String,
        colDataRetirada: String,
        colHoraRetirada: String,
        retiradoPorValue: String,
        dataRetiradaValue: String,
        horaRetiradaValue: String
    ): Pair<String, String> {
        val doc = parseXmlSafe(movementXml)
        val root = doc.documentElement

        val tmovElement = findFirstElementByLocalName(root, "TMOV")
            ?: throw MovementNotFoundException(idMov)

        val numeroMov = findFirstElementByLocalName(tmovElement, "NUMEROMOV")
            ?.textContent?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: idMov.toString()

        var tmovCompl = findFirstElementByLocalName(root, "TMOVCOMPL")
        if (tmovCompl == null) {
            tmovCompl = doc.createElement("TMOVCOMPL")
            appendElementWithText(doc, tmovCompl, "CODCOLIGADA", codColigada.toString())
            appendElementWithText(doc, tmovCompl, "IDMOV", idMov.toString())
            root.appendChild(tmovCompl)
        } else {
            // Verifica se já houve baixa anterior registrada nos 3 campos complementares
            val existingUser = findDirectChildIgnoreCase(tmovCompl, colRetiradoPor)?.textContent?.trim().orEmpty()
            val existingDate = findDirectChildIgnoreCase(tmovCompl, colDataRetirada)?.textContent?.trim().orEmpty()
            val existingTime = findDirectChildIgnoreCase(tmovCompl, colHoraRetirada)?.textContent?.trim().orEmpty()

            if (existingUser.isNotEmpty() && existingDate.isNotEmpty() && existingTime.isNotEmpty()) {
                throw MovementAlreadyWithdrawnException(idMov = idMov, retiradoPorExistente = existingUser)
            }

            upsertChildElement(doc, tmovCompl, "CODCOLIGADA", codColigada.toString())
            upsertChildElement(doc, tmovCompl, "IDMOV", idMov.toString())
        }

        upsertChildElement(doc, tmovCompl, colRetiradoPor, retiradoPorValue)
        upsertChildElement(doc, tmovCompl, colDataRetirada, dataRetiradaValue)
        upsertChildElement(doc, tmovCompl, colHoraRetirada, horaRetiradaValue)

        return serializeXmlWithoutDeclaration(doc) to numeroMov
    }

    /**
     * Valida o retorno de <SaveRecordResult>.
     * No TOTVS RM, sucesso retorna a PrimaryKey ("CODCOLIGADA;IDMOV", ex: "1;45820").
     * Quando ocorre erro de regra de negócio/permissão, o RM frequentemente retorna HTTP 200
     * com a mensagem de erro textual dentro de <SaveRecordResult>.
     */
    fun verifySaveRecordSuccess(soapResponseXml: String, codColigada: Int, idMov: Int) {
        val outerDoc = parseXmlSafe(soapResponseXml)
        checkSoapFault(outerDoc)

        val resultNode = findFirstElementByLocalName(outerDoc.documentElement, "SaveRecordResult")
            ?: throw TotvsRmIntegrationException("Resposta inválida do RM: tag SaveRecordResult ausente.")

        val resultText = resultNode.textContent?.trim().orEmpty()
        val expectedKey = "$codColigada;$idMov"

        if (resultText != expectedKey && !resultText.startsWith("$expectedKey;")) {
            throw TotvsRmIntegrationException(
                "TOTVS RM recusou a gravação da TMOVCOMPL no movimento 1.1.10 (IDMOV=$idMov): $resultText"
            )
        }
    }

    fun checkAutenticaAcessoSuccess(soapResponseXml: String): Boolean {
        val doc = parseXmlSafe(soapResponseXml)
        val fault = findFirstElementByLocalName(doc.documentElement, "Fault")
        if (fault != null) return false

        val result = findFirstElementByLocalName(doc.documentElement, "AutenticaAcessoResult")
            ?.textContent?.trim().orEmpty()
        // Em versões WCF do RM Host, AutenticaAcesso com Basic Auth válido retorna string vazia ou token sem Fault;
        // se contiver mensagem explícita de falha/exceção, rejeita.
        return !result.contains("inválid", ignoreCase = true) &&
            !result.contains("exception", ignoreCase = true) &&
            !result.contains("erro", ignoreCase = true)
    }

    private fun checkSoapFault(doc: Document) {
        val fault = findFirstElementByLocalName(doc.documentElement, "Fault") ?: return
        val faultString = findFirstElementByLocalName(fault, "faultstring")?.textContent?.trim()
            ?: fault.textContent?.trim()
            ?: "SOAP Fault desconhecido no TOTVS RM."
        throw TotvsRmIntegrationException("Erro SOAP retornado pelo TOTVS RM: $faultString")
    }

    private fun parseXmlSafe(xml: String): Document {
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            return factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trim())))
        } catch (ex: Exception) {
            throw TotvsRmIntegrationException("Falha ao interpretar XML retornado pelo TOTVS RM.", ex)
        }
    }

    private fun serializeXmlWithoutDeclaration(doc: Document): String {
        val transformer = TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }

    private fun findFirstElementByLocalName(parent: Element, localOrTagName: String): Element? {
        if ((parent.localName ?: parent.tagName).equals(localOrTagName, ignoreCase = true)) {
            return parent
        }
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val found = findFirstElementByLocalName(node as Element, localOrTagName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findDirectChildIgnoreCase(parent: Element, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val name = el.localName ?: el.tagName
                if (name.equals(tagName, ignoreCase = true)) return el
            }
        }
        return null
    }

    private fun upsertChildElement(doc: Document, parent: Element, tagName: String, value: String) {
        val existing = findDirectChildIgnoreCase(parent, tagName)
        if (existing != null) {
            existing.textContent = value
        } else {
            appendElementWithText(doc, parent, tagName.uppercase(), value)
        }
    }

    private fun appendElementWithText(doc: Document, parent: Element, tagName: String, value: String) {
        val el = doc.createElement(tagName)
        el.textContent = value
        parent.appendChild(el)
    }

    private fun escapeXml(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}

