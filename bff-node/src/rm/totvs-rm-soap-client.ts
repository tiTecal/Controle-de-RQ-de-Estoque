import { AppConfig } from '../config/env.js';
import { MovementItemDto, PendingMovementDto } from '../domain/types.js';
import { TotvsRmIntegrationError } from '../domain/errors.js';
import { TotvsRmGateway } from './totvs-rm-gateway.interface.js';
import { SoapXmlPayloads } from './soap-xml-payloads.js';

export class TotvsRmSoapClient implements TotvsRmGateway {
  private readonly wsBaseUrl: string;
  private readonly wsDataServerUrl: string;
  private readonly wsConsultaSqlUrl: string;

  private static readonly MOVIMENTO_DATASERVER = 'MovMovimentoTBCData';
  private static readonly ACTION_AUTENTICA = 'http://www.totvs.com/IwsBase/AutenticaAcesso';
  private static readonly ACTION_READ_RECORD = 'http://www.totvs.com/IwsDataServer/ReadRecord';
  private static readonly ACTION_SAVE_RECORD = 'http://www.totvs.com/IwsDataServer/SaveRecord';
  private static readonly ACTION_CONSULTA_SQL = 'http://www.totvs.com/IwsConsultaSQL/RealizarConsultaSQL';

  constructor(private readonly config: AppConfig) {
    this.wsBaseUrl = `${config.rmBaseUrl}/wsDataServer/IwsBase`;
    this.wsDataServerUrl = `${config.rmBaseUrl}/wsDataServer/IwsDataServer`;
    this.wsConsultaSqlUrl = `${config.rmBaseUrl}/wsConsultaSQL/IwsConsultaSQL`;
  }

  async authenticateRmUser(login: string, senha: string): Promise<boolean> {
    const cleanLogin = login.trim();
    if (!cleanLogin || !senha) return false;

    const envelope = SoapXmlPayloads.buildAutenticaAcessoEnvelope();

    try {
      const response = await fetch(this.wsBaseUrl, {
        method: 'POST',
        headers: {
          Authorization: this.basicAuthHeader(cleanLogin, senha),
          SOAPAction: `"${TotvsRmSoapClient.ACTION_AUTENTICA}"`,
          'Content-Type': 'text/xml; charset=utf-8'
        },
        body: envelope,
        signal: AbortSignal.timeout(20_000)
      });

      if (response.status === 401 || response.status === 403) {
        return false;
      }

      const body = await response.text();
      if (!response.ok) {
        return false;
      }

      return SoapXmlPayloads.checkAutenticaAcessoSuccess(body);
    } catch (ex: unknown) {
      throw new TotvsRmIntegrationError(
        `Não foi possível conectar ao servidor TOTVS RM (${this.config.rmBaseUrl}) para validar login.`,
        ex
      );
    }
  }

  async listPending1110Movements(codColigada: number): Promise<PendingMovementDto[]> {
    const envelope = SoapXmlPayloads.buildRealizarConsultaSqlEnvelope(
      this.config.rmSentencaPendentes,
      codColigada,
      this.config.rmCodSistemaSql,
      `CODCOLIGADA=${codColigada}`
    );

    const responseXml = await this.postSoapWithServiceAccount(
      this.wsConsultaSqlUrl,
      TotvsRmSoapClient.ACTION_CONSULTA_SQL,
      envelope
    );

    const rows = SoapXmlPayloads.parseConsultaSqlRows(responseXml);

    const result: PendingMovementDto[] = [];
    for (const row of rows) {
      const idMov = parseInt(row['IDMOV'] || '', 10);
      if (isNaN(idMov) || idMov <= 0) continue;

      result.push({
        codColigada: parseInt(row['CODCOLIGADA'] || '', 10) || codColigada,
        idMov,
        numeroMov: row['NUMEROMOV']?.trim() || idMov.toString(),
        codTmv: row['CODTMV']?.trim() || '1.1.10',
        codFilial: parseInt(row['CODFILIAL'] || '', 10) || 1,
        codLoc: row['CODLOC']?.trim() || '',
        nomeLocal: row['NOMELOCAL']?.trim() || 'Almoxarifado Principal',
        dataEmissao: row['DATAEMISSAO']?.trim() || '',
        usuarioEstoquista: row['USUARIOESTOQUISTA']?.trim() || '',
        codCCusto: row['CODCCUSTO']?.trim() || '',
        nomeCCusto: row['NOMECCUSTO']?.trim() || '',
        numeroRequisicao1113: row['NUMEROREQUISICAO1113']?.trim() || '',
        solicitante: row['SOLICITANTE']?.trim() || '',
        qtdItens: parseInt(row['QTDITENS'] || '', 10) || 0
      });
    }

    return result;
  }

  async listMovement1110Items(codColigada: number, idMov: number): Promise<MovementItemDto[]> {
    const envelope = SoapXmlPayloads.buildRealizarConsultaSqlEnvelope(
      this.config.rmSentencaItens,
      codColigada,
      this.config.rmCodSistemaSql,
      `CODCOLIGADA=${codColigada};IDMOV=${idMov}`
    );

    const responseXml = await this.postSoapWithServiceAccount(
      this.wsConsultaSqlUrl,
      TotvsRmSoapClient.ACTION_CONSULTA_SQL,
      envelope
    );

    const rows = SoapXmlPayloads.parseConsultaSqlRows(responseXml);

    return rows.map((row, idx) => {
      const rawQtd = (row['QUANTIDADE'] || '0').replace(',', '.');
      return {
        codColigada: parseInt(row['CODCOLIGADA'] || '', 10) || codColigada,
        idMov: parseInt(row['IDMOV'] || '', 10) || idMov,
        nSeqItmMov: parseInt(row['NSEQITMMOV'] || '', 10) || idx + 1,
        idPrd: parseInt(row['IDPRD'] || '', 10) || 0,
        codigoPrd: row['CODIGOPRD']?.trim() || '',
        descricaoProduto: row['DESCRICAOPRODUTO']?.trim() || '',
        codUnd: row['CODUND']?.trim() || 'UN',
        quantidade: parseFloat(rawQtd) || 0,
        codigoBarra: row['CODIGOBARRA']?.trim() || '',
        observacaoItem: row['OBSERVACAOITEM']?.trim() || ''
      };
    });
  }

  async saveWithdrawalComplementaryFields(
    codColigada: number,
    idMov: number,
    retiradoPor: string,
    dataRetirada: string,
    horaRetirada: string
  ): Promise<string> {
    const primaryKey = `${codColigada};${idMov}`;

    // 1. Lê o registro atual do movimento 1.1.10 via MovMovimentoTBCData
    const readEnvelope = SoapXmlPayloads.buildReadRecordEnvelope(
      TotvsRmSoapClient.MOVIMENTO_DATASERVER,
      primaryKey,
      this.config.rmContextoDataServer
    );
    const readResponseXml = await this.postSoapWithServiceAccount(
      this.wsDataServerUrl,
      TotvsRmSoapClient.ACTION_READ_RECORD,
      readEnvelope
    );
    const currentMovementXml = SoapXmlPayloads.extractReadRecordXml(readResponseXml, idMov);

    // 2. Injeta cirurgicamente retirado_por, data_retirada e hora_retirada na TMOVCOMPL
    const { updatedXml, numeroMov } = SoapXmlPayloads.injectTmovComplWithdrawalFields(
      currentMovementXml,
      codColigada,
      idMov,
      this.config.colRetiradoPor,
      this.config.colDataRetirada,
      this.config.colHoraRetirada,
      retiradoPor,
      dataRetirada,
      horaRetirada
    );

    // 3. Salva o movimento atualizado via SaveRecord no MovMovimentoTBCData
    const saveEnvelope = SoapXmlPayloads.buildSaveRecordEnvelope(
      TotvsRmSoapClient.MOVIMENTO_DATASERVER,
      updatedXml,
      this.config.rmContextoDataServer
    );
    const saveResponseXml = await this.postSoapWithServiceAccount(
      this.wsDataServerUrl,
      TotvsRmSoapClient.ACTION_SAVE_RECORD,
      saveEnvelope
    );
    SoapXmlPayloads.verifySaveRecordSuccess(saveResponseXml, codColigada, idMov);

    return numeroMov;
  }

  private async postSoapWithServiceAccount(
    url: string,
    soapAction: string,
    envelopeXml: string
  ): Promise<string> {
    let response: Response;
    try {
      response = await fetch(url, {
        method: 'POST',
        headers: {
          Authorization: this.basicAuthHeader(
            this.config.rmServiceUser,
            this.config.rmServicePassword
          ),
          SOAPAction: `"${soapAction}"`,
          'Content-Type': 'text/xml; charset=utf-8'
        },
        body: envelopeXml,
        signal: AbortSignal.timeout(20_000)
      });
    } catch (ex: unknown) {
      throw new TotvsRmIntegrationError(
        `Falha de conexão com o servidor TOTVS RM em ${url}.`,
        ex
      );
    }

    if (response.status === 401 || response.status === 403) {
      throw new TotvsRmIntegrationError(
        `Conta de serviço '${this.config.rmServiceUser}' sem autorização no TOTVS RM (${response.status}).`
      );
    }

    return await response.text();
  }

  private basicAuthHeader(user: string, pass: string): string {
    const raw = `${user}:${pass}`;
    return `Basic ${Buffer.from(raw, 'utf-8').toString('base64')}`;
  }
}

