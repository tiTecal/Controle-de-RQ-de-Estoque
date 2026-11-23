import { DOMParser, XMLSerializer } from '@xmldom/xmldom';
import {
  MovementAlreadyWithdrawnError,
  MovementNotFoundError,
  TotvsRmIntegrationError
} from '../domain/errors.js';

export class SoapXmlPayloads {
  static buildAutenticaAcessoEnvelope(): string {
    return `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tot="http://www.totvs.com/">
   <soapenv:Header/>
   <soapenv:Body>
      <tot:AutenticaAcesso/>
   </soapenv:Body>
</soapenv:Envelope>`.trim();
  }

  static buildRealizarConsultaSqlEnvelope(
    codSentenca: string,
    codColigada: number,
    codSistema: string,
    parameters: string
  ): string {
    return `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tot="http://www.totvs.com/">
   <soapenv:Header/>
   <soapenv:Body>
      <tot:RealizarConsultaSQL>
         <tot:codSentenca>${this.escapeXml(codSentenca)}</tot:codSentenca>
         <tot:codColigada>${codColigada}</tot:codColigada>
         <tot:codSistema>${this.escapeXml(codSistema)}</tot:codSistema>
         <tot:parameters>${this.escapeXml(parameters)}</tot:parameters>
      </tot:RealizarConsultaSQL>
   </soapenv:Body>
</soapenv:Envelope>`.trim();
  }

  static buildReadRecordEnvelope(
    dataServerName: string,
    primaryKey: string,
    contexto: string
  ): string {
    return `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tot="http://www.totvs.com/">
   <soapenv:Header/>
   <soapenv:Body>
      <tot:ReadRecord>
         <tot:DataServerName>${this.escapeXml(dataServerName)}</tot:DataServerName>
         <tot:PrimaryKey>${this.escapeXml(primaryKey)}</tot:PrimaryKey>
         <tot:Contexto>${this.escapeXml(contexto)}</tot:Contexto>
      </tot:ReadRecord>
   </soapenv:Body>
</soapenv:Envelope>`.trim();
  }

  static buildSaveRecordEnvelope(
    dataServerName: string,
    recordXml: string,
    contexto: string
  ): string {
    return `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tot="http://www.totvs.com/">
   <soapenv:Header/>
   <soapenv:Body>
      <tot:SaveRecord>
         <tot:DataServerName>${this.escapeXml(dataServerName)}</tot:DataServerName>
         <tot:XML><![CDATA[${recordXml}]]></tot:XML>
         <tot:Contexto>${this.escapeXml(contexto)}</tot:Contexto>
      </tot:SaveRecord>
   </soapenv:Body>
</soapenv:Envelope>`.trim();
  }

  /**
   * Extrai as linhas <Resultado> retornadas pelo método RealizarConsultaSQL do TOTVS RM.
   * As chaves do mapa são normalizadas para UPPERCASE para leitura consistente.
   */
  static parseConsultaSqlRows(soapResponseXml: string): Array<Record<string, string>> {
    const outerDoc = this.parseXmlSafe(soapResponseXml);
    this.checkSoapFault(outerDoc);

    const resultNode = this.findFirstElementByLocalName(
      outerDoc.documentElement,
      'RealizarConsultaSQLResult'
    );
    if (!resultNode) return [];

    const innerXml = (resultNode.textContent || '').trim();
    if (!innerXml || innerXml === '<NewDataSet />' || innerXml === '<NewDataSet/>') {
      return [];
    }

    const innerDoc = this.parseXmlSafe(innerXml);
    const resultadoNodes = innerDoc.documentElement.childNodes;
    const rows: Array<Record<string, string>> = [];

    for (let i = 0; i < resultadoNodes.length; i++) {
      const node = resultadoNodes.item(i);
      if (
        node &&
        node.nodeType === 1 && // Node.ELEMENT_NODE
        (node.nodeName || '').toLowerCase() === 'resultado'
      ) {
        const rowMap: Record<string, string> = {};
        const fields = node.childNodes;
        for (let j = 0; j < fields.length; j++) {
          const field = fields.item(j);
          if (field && field.nodeType === 1) {
            const fieldName = (field.nodeName || '').toUpperCase();
            rowMap[fieldName] = (field.textContent || '').trim();
          }
        }
        rows.push(rowMap);
      }
    }

    return rows;
  }

  /**
   * Extrai o XML interno de <ReadRecordResult> do MovMovimentoTBCData.
   */
  static extractReadRecordXml(soapResponseXml: string, idMov: number): string {
    const outerDoc = this.parseXmlSafe(soapResponseXml);
    this.checkSoapFault(outerDoc);

    const resultNode = this.findFirstElementByLocalName(
      outerDoc.documentElement,
      'ReadRecordResult'
    );
    if (!resultNode) {
      throw new TotvsRmIntegrationError('Resposta inválida do RM: tag ReadRecordResult ausente.');
    }

    const innerXml = (resultNode.textContent || '').trim();
    if (!innerXml || !/tmov/i.test(innerXml)) {
      throw new MovementNotFoundError(idMov);
    }
    return innerXml;
  }

  /**
   * Atualiza cirurgicamente apenas os 3 campos complementares (RETIRADO_POR, DATA_RETIRADA, HORA_RETIRADA)
   * dentro de <TMOVCOMPL> no XML do MovMovimentoTBCData, preservando <TMOV> e <TITMMOV> intactos.
   */
  static injectTmovComplWithdrawalFields(
    movementXml: string,
    codColigada: number,
    idMov: number,
    colRetiradoPor: string,
    colDataRetirada: string,
    colHoraRetirada: string,
    retiradoPorValue: string,
    dataRetiradaValue: string,
    horaRetiradaValue: string
  ): { updatedXml: string; numeroMov: string } {
    const doc = this.parseXmlSafe(movementXml);
    const root = doc.documentElement;

    const tmovElement = this.findFirstElementByLocalName(root, 'TMOV');
    if (!tmovElement) {
      throw new MovementNotFoundError(idMov);
    }

    const numeroMovNode = this.findFirstElementByLocalName(tmovElement, 'NUMEROMOV');
    const numeroMov = (numeroMovNode?.textContent || '').trim() || idMov.toString();

    let tmovCompl = this.findFirstElementByLocalName(root, 'TMOVCOMPL');
    if (!tmovCompl) {
      tmovCompl = doc.createElement('TMOVCOMPL');
      this.appendElementWithText(doc, tmovCompl, 'CODCOLIGADA', codColigada.toString());
      this.appendElementWithText(doc, tmovCompl, 'IDMOV', idMov.toString());
      root.appendChild(tmovCompl);
    } else {
      // Verifica se já houve baixa anterior registrada nos 3 campos complementares
      const existingUser = (
        this.findDirectChildIgnoreCase(tmovCompl, colRetiradoPor)?.textContent || ''
      ).trim();
      const existingDate = (
        this.findDirectChildIgnoreCase(tmovCompl, colDataRetirada)?.textContent || ''
      ).trim();
      const existingTime = (
        this.findDirectChildIgnoreCase(tmovCompl, colHoraRetirada)?.textContent || ''
      ).trim();

      if (existingUser && existingDate && existingTime) {
        throw new MovementAlreadyWithdrawnError(idMov, existingUser);
      }

      this.upsertChildElement(doc, tmovCompl, 'CODCOLIGADA', codColigada.toString());
      this.upsertChildElement(doc, tmovCompl, 'IDMOV', idMov.toString());
    }

    this.upsertChildElement(doc, tmovCompl, colRetiradoPor, retiradoPorValue);
    this.upsertChildElement(doc, tmovCompl, colDataRetirada, dataRetiradaValue);
    this.upsertChildElement(doc, tmovCompl, colHoraRetirada, horaRetiradaValue);

    return {
      updatedXml: this.serializeXmlWithoutDeclaration(doc),
      numeroMov
    };
  }

  /**
   * Valida o retorno de <SaveRecordResult>.
   * No TOTVS RM, sucesso retorna a PrimaryKey ("CODCOLIGADA;IDMOV", ex: "1;45820").
   */
  static verifySaveRecordSuccess(
    soapResponseXml: string,
    codColigada: number,
    idMov: number
  ): void {
    const outerDoc = this.parseXmlSafe(soapResponseXml);
    this.checkSoapFault(outerDoc);

    const resultNode = this.findFirstElementByLocalName(
      outerDoc.documentElement,
      'SaveRecordResult'
    );
    if (!resultNode) {
      throw new TotvsRmIntegrationError('Resposta inválida do RM: tag SaveRecordResult ausente.');
    }

    const resultText = (resultNode.textContent || '').trim();
    const expectedKey = `${codColigada};${idMov}`;

    if (resultText !== expectedKey && !resultText.startsWith(`${expectedKey};`)) {
      throw new TotvsRmIntegrationError(
        `TOTVS RM recusou a gravação da TMOVCOMPL no movimento 1.1.10 (IDMOV=${idMov}): ${resultText}`
      );
    }
  }

  static checkAutenticaAcessoSuccess(soapResponseXml: string): boolean {
    const doc = this.parseXmlSafe(soapResponseXml);
    const fault = this.findFirstElementByLocalName(doc.documentElement, 'Fault');
    if (fault) return false;

    const result = (
      this.findFirstElementByLocalName(doc.documentElement, 'AutenticaAcessoResult')?.textContent || ''
    ).trim();

    return (
      !/inv[áa]lid/i.test(result) &&
      !/exception/i.test(result) &&
      !/erro/i.test(result)
    );
  }

  private static checkSoapFault(doc: Document): void {
    const fault = this.findFirstElementByLocalName(doc.documentElement, 'Fault');
    if (!fault) return;

    const faultString = (
      this.findFirstElementByLocalName(fault, 'faultstring')?.textContent ||
      fault.textContent ||
      'SOAP Fault desconhecido no TOTVS RM.'
    ).trim();

    throw new TotvsRmIntegrationError(`Erro SOAP retornado pelo TOTVS RM: ${faultString}`);
  }

  public static parseXmlSafe(xml: string): Document {
    try {
      const parser = new DOMParser({
        errorHandler: {
          warning: () => {},
          error: (msg: string) => {
            throw new Error(msg);
          },
          fatalError: (msg: string) => {
            throw new Error(msg);
          }
        }
      });
      return parser.parseFromString(xml.trim(), 'text/xml');
    } catch (ex: unknown) {
      throw new TotvsRmIntegrationError('Falha ao interpretar XML retornado pelo TOTVS RM.', ex);
    }
  }

  private static serializeXmlWithoutDeclaration(doc: Document): string {
    const serializer = new XMLSerializer();
    let serialized = serializer.serializeToString(doc);
    // Remove qualquer declaração <?xml ...?> inicial se presente
    serialized = serialized.replace(/^\s*<\?xml[^>]*\?>/i, '').trim();
    return serialized;
  }

  private static findFirstElementByLocalName(parent: Element, localOrTagName: string): Element | null {
    const name = parent.localName || parent.tagName;
    if (name && name.toLowerCase() === localOrTagName.toLowerCase()) {
      return parent;
    }
    const children = parent.childNodes;
    for (let i = 0; i < children.length; i++) {
      const node = children.item(i);
      if (node && node.nodeType === 1) {
        const found = this.findFirstElementByLocalName(node as Element, localOrTagName);
        if (found) return found;
      }
    }
    return null;
  }

  private static findDirectChildIgnoreCase(parent: Element, tagName: string): Element | null {
    const children = parent.childNodes;
    for (let i = 0; i < children.length; i++) {
      const node = children.item(i);
      if (node && node.nodeType === 1) {
        const el = node as Element;
        const name = el.localName || el.tagName;
        if (name && name.toLowerCase() === tagName.toLowerCase()) {
          return el;
        }
      }
    }
    return null;
  }

  private static upsertChildElement(
    doc: Document,
    parent: Element,
    tagName: string,
    value: string
  ): void {
    const existing = this.findDirectChildIgnoreCase(parent, tagName);
    if (existing) {
      existing.textContent = value;
    } else {
      this.appendElementWithText(doc, parent, tagName.toUpperCase(), value);
    }
  }

  private static appendElementWithText(
    doc: Document,
    parent: Element,
    tagName: string,
    value: string
  ): void {
    const el = doc.createElement(tagName);
    el.textContent = value;
    parent.appendChild(el);
  }

  private static escapeXml(input: string): string {
    return input
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  }
}

