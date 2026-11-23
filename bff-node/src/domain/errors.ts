export class InvalidRmCredentialsError extends Error {
  public readonly isWithdrawalConfirmation: boolean;

  constructor(
    message: string = 'Login ou senha inválidos no TOTVS RM.',
    isWithdrawalConfirmation: boolean = false
  ) {
    super(message);
    this.name = 'InvalidRmCredentialsError';
    this.isWithdrawalConfirmation = isWithdrawalConfirmation;
  }
}

export class MovementAlreadyWithdrawnError extends Error {
  public readonly idMov: number;
  public readonly retiradoPorExistente: string;

  constructor(idMov: number, retiradoPorExistente: string) {
    super(
      `O movimento 1.1.10 (IDMOV=${idMov}) já possui baixa de retirada registrada por '${retiradoPorExistente}'.`
    );
    this.name = 'MovementAlreadyWithdrawnError';
    this.idMov = idMov;
    this.retiradoPorExistente = retiradoPorExistente;
  }
}

export class MovementNotFoundError extends Error {
  public readonly idMov: number;

  constructor(idMov: number) {
    super(`Movimento 1.1.10 (IDMOV=${idMov}) não encontrado no TOTVS RM.`);
    this.name = 'MovementNotFoundError';
    this.idMov = idMov;
  }
}

export class BusinessValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BusinessValidationError';
  }
}

export class TotvsRmIntegrationError extends Error {
  public readonly cause?: unknown;

  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = 'TotvsRmIntegrationError';
    this.cause = cause;
  }
}

