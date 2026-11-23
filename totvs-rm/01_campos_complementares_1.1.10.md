# Guia de Parametrização no TOTVS RM — Movimento `1.1.10` (Baixa de Estoque)

Siga estes 4 passos no ambiente **TOTVS RM (Gestão de Estoque, Compras e Faturamento / Nucleus)** da Tecal para habilitar a listagem e a baixa de retirada física pelo tablet da porta.

---

## Passo 1: Criar os 3 Campos Complementares na `TMOVCOMPL`

No TOTVS RM, acesse **Gestão de Estoque, Compras e Faturamento -> Cadastros -> Campos Complementares -> Movimento** e cadastre exatamente as colunas abaixo:

| Nome da Coluna (`TMOVCOMPL`) | Tipo de Dado Recomendado | Tamanho | Título Sugerido no RM | Finalidade |
| :--- | :--- | :---: | :--- | :--- |
| `RETIRADO_POR` (`retirado_por`) | Alfanumérico (`VARCHAR`) | `60` | Retirado Por | Login (`CODUSUARIO`) de quem colocou login e senha no tablet ao retirar 100% dos itens. |
| `DATA_RETIRADA` (`data_retirada`) | Data (`DATETIME` ou `VARCHAR(10)`) | `10` | Data da Retirada | Data oficial da confirmação de retirada (`YYYY-MM-DD`). |
| `HORA_RETIRADA` (`hora_retirada`) | Alfanumérico (`VARCHAR`) | `8` | Hora da Retirada | Hora oficial da confirmação de retirada (`HH:MM:SS`). |

> **Observação:** Em SQL Server (banco padrão do TOTVS RM), os identificadores de coluna são *case-insensitive*, enquanto no XML do DataServer `MovMovimentoTBCData` o RM exporta as tags de `TMOVCOMPL` em **MAIÚSCULAS** (`<RETIRADO_POR>`, `<DATA_RETIRADA>`, `<HORA_RETIRADA>`). O BFF já trata essa correspondência de forma case-insensitive automaticamente.

---

## Passo 2: Vincular Campos e Permissão de Edição no Movimento `1.1.10`

1. Acesse **Ambiente -> Parâmetros -> Gestão de Estoque, Compras e Faturamento -> Tipos de Movimento** e edite o movimento **`1.1.10` (*Baixa de Estoque*)**.
2. Na etapa **Campos Complementares**, habilite `RETIRADO_POR`, `DATA_RETIRADA` e `HORA_RETIRADA` como **Visíveis** e **Editáveis** (deixe **desmarcada** a obrigatoriedade na inclusão, pois quando o estoquista fatura o `1.1.13` -> `1.1.10` no computador esses três campos devem nascer em branco).
3. Garanta que o parâmetro de **Edição de Campos Complementares** esteja permitido no movimento `1.1.10` após a gravação/baixa de estoque para o perfil do **Usuário de Serviço** configurado no BFF (`RM_SERVICE_USER`).

---

## Passo 3: Cadastrar as 2 Sentenças SQL (`wsConsultaSQL`)

Acesse **Gestão -> Visões de Dados / Consultas SQL** no módulo **`T` (Estoque/Nucleus)** e cadastre:

1. **Código:** `TECAL.ALM.1110_PENDENTES`
   * **Título:** `[Almoxarifado Tablet] Movimentos 1.1.10 Pendentes de Retirada`
   * **SQL:** Conteúdo de [`02_TECAL.ALM.1110_PENDENTES.sql`](./02_TECAL.ALM.1110_PENDENTES.sql)
2. **Código:** `TECAL.ALM.1110_ITENS`
   * **Título:** `[Almoxarifado Tablet] Itens do Movimento 1.1.10`
   * **SQL:** Conteúdo de [`03_TECAL.ALM.1110_ITENS.sql`](./03_TECAL.ALM.1110_ITENS.sql)

Conceda permissão de execução nessas duas consultas ao perfil do `RM_SERVICE_USER`.

---

## Passo 4: Validar WebServices Nativos no `RM.Host`

Confirme que o `RM.Host` responde na rede interna nos serviços SOAP padrão:
* `http://<SERVIDOR_RM>:<PORTA>/wsConsultaSQL/IwsConsultaSQL`
* `http://<SERVIDOR_RM>:<PORTA>/wsDataServer/IwsDataServer` (operações `AutenticaAcesso`, `ReadRecord` e `SaveRecord` para `MovMovimentoTBCData`)
