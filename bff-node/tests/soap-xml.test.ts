import { describe, expect, it } from 'vitest';
import { MovementAlreadyWithdrawnError, TotvsRmIntegrationError } from '../src/domain/errors.js';
import { SoapXmlPayloads } from '../src/rm/soap-xml-payloads.js';

describe('SoapXmlPayloads', () => {
  it('deve injetar retirado_por, data_retirada e hora_retirada na TMOVCOMPL preservando TMOV e TITMMOV', () => {
    const sampleRmXml = `
      <MovMovimento>
        <TMOV>
          <CODCOLIGADA>1</CODCOLIGADA>
          <IDMOV>45812</IDMOV>
          <NUMEROMOV>009418</NUMEROMOV>
          <CODTMV>1.1.10</CODTMV>
        </TMOV>
        <TITMMOV>
          <CODCOLIGADA>1</CODCOLIGADA>
          <IDMOV>45812</IDMOV>
          <NSEQITMMOV>1</NSEQITMMOV>
          <IDPRD>1042</IDPRD>
          <QUANTIDADE>10.0000</QUANTIDADE>
        </TITMMOV>
        <TMOVCOMPL>
          <CODCOLIGADA>1</CODCOLIGADA>
          <IDMOV>45812</IDMOV>
        </TMOVCOMPL>
      </MovMovimento>
    `.trim();

    const { updatedXml, numeroMov } = SoapXmlPayloads.injectTmovComplWithdrawalFields(
      sampleRmXml,
      1,
      45812,
      'RETIRADO_POR',
      'DATA_RETIRADA',
      'HORA_RETIRADA',
      'carlos.silva',
      '2026-10-30',
      '08:45:12'
    );

    expect(numeroMov).toBe('009418');
    expect(updatedXml).toContain('<RETIRADO_POR>carlos.silva</RETIRADO_POR>');
    expect(updatedXml).toContain('<DATA_RETIRADA>2026-10-30</DATA_RETIRADA>');
    expect(updatedXml).toContain('<HORA_RETIRADA>08:45:12</HORA_RETIRADA>');
    expect(updatedXml).toContain('<CODTMV>1.1.10</CODTMV>');
    expect(updatedXml).toContain('<QUANTIDADE>10.0000</QUANTIDADE>');

    // Tentar dar baixa uma segunda vez no mesmo XML já preenchido deve lançar erro 409
    expect(() => {
      SoapXmlPayloads.injectTmovComplWithdrawalFields(
        updatedXml,
        1,
        45812,
        'RETIRADO_POR',
        'DATA_RETIRADA',
        'HORA_RETIRADA',
        'outro.usuario',
        '2026-10-30',
        '09:00:00'
      );
    }).toThrow(MovementAlreadyWithdrawnError);
  });

  it('deve extrair linhas da consulta SQL com chaves em caixa alta', () => {
    const sampleConsultaXml = `
      <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
        <s:Body>
          <RealizarConsultaSQLResponse xmlns="http://www.totvs.com/">
            <RealizarConsultaSQLResult>
              &lt;NewDataSet&gt;
                &lt;Resultado&gt;
                  &lt;CODCOLIGADA&gt;1&lt;/CODCOLIGADA&gt;
                  &lt;IDMOV&gt;45812&lt;/IDMOV&gt;
                  &lt;NUMEROMOV&gt;009418&lt;/NUMEROMOV&gt;
                  &lt;qtdItens&gt;3&lt;/qtdItens&gt;
                &lt;/Resultado&gt;
              &lt;/NewDataSet&gt;
            </RealizarConsultaSQLResult>
          </RealizarConsultaSQLResponse>
        </s:Body>
      </s:Envelope>
    `.trim();

    const rows = SoapXmlPayloads.parseConsultaSqlRows(sampleConsultaXml);
    expect(rows).toHaveLength(1);
    expect(rows[0]['CODCOLIGADA']).toBe('1');
    expect(rows[0]['IDMOV']).toBe('45812');
    expect(rows[0]['NUMEROMOV']).toBe('009418');
    expect(rows[0]['QTDITENS']).toBe('3');
  });

  it('deve verificar sucesso de gravação e lançar erro se o RM recusar', () => {
    const successXml = `
      <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body>
          <SaveRecordResponse xmlns="http://www.totvs.com/">
            <SaveRecordResult>1;45812</SaveRecordResult>
          </SaveRecordResponse>
        </soap:Body>
      </soap:Envelope>
    `.trim();

    expect(() => {
      SoapXmlPayloads.verifySaveRecordSuccess(successXml, 1, 45812);
    }).not.toThrow();

    const failXml = `
      <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body>
          <SaveRecordResponse xmlns="http://www.totvs.com/">
            <SaveRecordResult>Erro de validação: Usuário sem permissão no movimento 1.1.10</SaveRecordResult>
          </SaveRecordResponse>
        </soap:Body>
      </soap:Envelope>
    `.trim();

    expect(() => {
      SoapXmlPayloads.verifySaveRecordSuccess(failXml, 1, 45812);
    }).toThrow(TotvsRmIntegrationError);
  });
});

